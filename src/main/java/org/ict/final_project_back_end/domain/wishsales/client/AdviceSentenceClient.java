package org.ict.final_project_back_end.domain.wishsales.client;

import lombok.extern.slf4j.Slf4j;
import org.ict.final_project_back_end.domain.wishsales.dto.BuyAdviceDtos.BuyAdviceResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// 구매 조언 문장 만들기 (배치 전용). 여러 게임을 한 번의 요청에 담아 전체 요약(overall) + 게임별 문장 목록을 받는다.
// AI_PROVIDERS 환경변수에 순서대로 적은 공급자(AdviceTextProvider)를 차례로 시도하고,
// 앞 공급자가 키가 없거나 실패하면(한도 초과, 오류 등) 다음 공급자로 넘어간다. 지원: anthropic | gemini | groq
// 모두 실패하면 null을 돌려주고, 호출하는 쪽이 규칙 문장을 쓴다.
// 로그: 정상 흐름은 info 한 줄, 원문(raw)은 debug로 앞 300자만. 키가 없어 건너뛰는 것은 debug.
//       HTTP 오류는 warn(한도/과부하는 일시적), 파싱 오류는 error.
@Slf4j
@Component
public class AdviceSentenceClient {
    private static final int LOG_PREVIEW_LEN = 300; // 로그에 찍을 원문 최대 길이

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<String> order; // 시도할 공급자 순서
    private final Map<String, AdviceTextProvider> providers;

    // AI 배치 결과: 전체 요약 한 문단 + 게임별 문장 목록
    public record BatchResult(String overall, List<String> sentences) {}

    public AdviceSentenceClient(@Value("${AI_PROVIDERS:${AI_PROVIDER:anthropic}}") String order,
                                List<AdviceTextProvider> providers) {
        // "gemini,groq" 같은 문자열을 공백 없이 소문자 목록으로 바꾼다
        this.order = Arrays.stream((order == null ? "anthropic" : order).split(","))
                .map(v -> v.trim().toLowerCase())
                .filter(v -> !v.isEmpty())
                .toList();
        this.providers = providers.stream().collect(Collectors.toMap(AdviceTextProvider::name, Function.identity()));
        // 시작할 때 공급자 설정과 키 유무를 한 번 찍어 둔다 (키 값은 찍지 않는다)
        log.info("[AI] 공급자 순서={}, 키 있음: anthropic={}, gemini={}, groq={}",
                this.order, hasKey("anthropic"), hasKey("gemini"), hasKey("groq"));
    }

    // 고른 공급자의 키가 설정되어 있는지
    public boolean isConfigured() {
        return order.stream().anyMatch(this::hasKey);
    }

    private boolean hasKey(String provider) {
        AdviceTextProvider p = providers.get(provider);
        return p != null && p.configured();
    }

    // 전체 요약 + 게임별 문장 목록을 돌려준다. 실패하면 null.
    public BatchResult explainBatch(List<BuyAdviceResponse> items) {
        if (!isConfigured() || items.isEmpty()) {
            log.info("[AI] 호출 안 함: configured={}, items={}", isConfigured(), items.size());
            return null;
        }
        try {
            String prompt = prompt(items);
            // 공급자를 순서대로 시도한다. 키가 없거나 호출이 실패하면 다음 공급자로 넘어간다
            for (String provider : order) {
                if (!hasKey(provider)) {
                    log.debug("[AI:{}] 키가 없어서 건너뜀", provider);
                    continue;
                }
                try {
                    log.info("[AI:{}] 호출 시작 (게임 {}개)", provider, items.size());
                    String text = providers.get(provider).complete(prompt);
                    // 원문은 길어서 debug로 내리고 앞부분만 남긴다 (필요하면 로그 레벨을 DEBUG로 올린다)
                    log.debug("[AI:{}] raw: {}", provider, preview(text));
                    BatchResult result = parse(provider, text, items.size());
                    if (result != null) {
                        log.info("[AI:{}] 성공: overall={}, sentences={}", provider, result.overall() != null, result.sentences().size());
                        return result;
                    }
                } catch (RestClientResponseException e) {
                    // HTTP 오류(한도 초과, 과부하 등)는 일시적일 수 있어서 warn. 응답 본문에 원인이 있다
                    log.warn("[AI:{}] HTTP {} 응답 본문: {}", provider, e.getStatusCode(), preview(e.getResponseBodyAsString()));
                } catch (Exception e) {
                    // 파싱 실패, NPE 등은 코드나 프롬프트 문제일 수 있어서 error
                    log.error("[AI:{}] 호출/파싱 실패: {}", provider, e.toString(), e);
                }
            }
            log.warn("[AI] 모든 공급자 실패 → 규칙 문장 사용");
            return null;
        } catch (Exception e) {
            // 프롬프트 생성 등 바깥 단계 실패
            log.error("explainBatch 전체 실패", e);
            return null;
        }
    }

    // 공통 프롬프트: 판단은 이미 정해졌으니 바꾸지 말고, 주어진 숫자로 문장만 쓰게 한다.
    // overall(전체 요약) + sentences(게임별 문장)를 한 번에 받는다
    private String prompt(List<BuyAdviceResponse> items) {
        return "당신은 게임 할인 전문 어드바이저입니다. "
                + "아래 JSON 배열은 이미 계산된 게임별 할인 판단 결과입니다.\n\n"
                + "규칙:\n"
                + "1. 각 게임마다 친구에게 조언하듯이 자연스러운 한국어 문장 1~2개만 쓰세요.\n"
                + "2. 판단(WAIT/BUY)을 먼저 말하고, 이유를 한 문장 이내로 덧붙이세요.\n"
                + "3. expectedPercent와 expectedMonth는 문장 안에 자연스럽게 녹이세요. "
                + "예: '11월쯤 60% 정도 할인을 기대해 볼 만해요.'\n"
                + "4. 숫자를 나열하거나 보고서처럼 쓰지 마세요.\n"
                + "5. WAIT이면 '지금은 조금 더 기다려 보는 게 나을 수 있어요' 톤으로, "
                + "BUY이면 '지금 사도 괜찮아요' 톤으로 말하세요.\n"
                + "판단이 안 된 게임(verdict가 UNKNOWN)은 숫자를 만들지 말고, reason 값을 바탕으로 "
                + "'왜 지금은 판단하기 어려운지' 친근하게 한두 문장으로 설명하세요. "
                + "reason의 내용을 자연스럽게 풀어 쓰세요.\n"
                + "6. JSON에 없는 숫자나 사실은 만들지 마세요.\n"
                + "7. '과거 기록 기반', '작년 기록' 같은 말은 넣지 마세요. 대신 '할인 기록'이라고 말하세요.\n"
                + "8. 위시리스트 전체에 대한 한 문단 요약(overall)도 쓰세요. "
                + "BUY인 게임과 WAIT인 게임을 구분해 조언하는 톤으로, 2~3문장 이내로 작성하세요.\n"
                + "9. 결과는 반드시 아래 JSON 형식만 출력하세요. 다른 설명은 쓰지 마세요.\n"
                + "{\n"
                + "  \"overall\": \"전체 요약 한 문단\",\n"
                + "  \"sentences\": [\"게임1 문장\", \"게임2 문장\", ...]\n"
                + "}\n"
                + "sentences는 입력과 같은 순서, 같은 개수여야 합니다.\n\n"
                + mapper.writeValueAsString(items);
    }

    // 모델 답을 결과로 바꾼다. 문장 개수가 게임 수와 다르면 null (다음 공급자로 넘어간다)
    private BatchResult parse(String provider, String text, int expected) {
        // 모델이 ```json 같은 표시를 붙였으면 떼고 파싱한다
        String clean = text.replace("```json", "").replace("```", "").trim();
        JsonNode node = mapper.readTree(clean);

        String overall = null;
        List<String> sentences = null;
        if (node.isArray()) {
            // 예전 형식: 문자열 배열만 온 경우
            sentences = mapper.convertValue(node, new TypeReference<List<String>>() {});
        } else if (node.isObject()) {
            // 새 형식: { overall, sentences }
            overall = node.path("overall").asText(null);
            if (overall != null && overall.isBlank()) overall = null;
            JsonNode sentencesNode = node.path("sentences");
            if (sentencesNode.isArray()) {
                sentences = mapper.convertValue(sentencesNode, new TypeReference<List<String>>() {});
            }
        }
        if (sentences != null && sentences.size() == expected) {
            return new BatchResult(overall, sentences);
        }
        // 개수가 안 맞거나 문장이 없으면 어떤 값이 왔는지 남긴다
        log.warn("[AI:{}] 문장 개수 불일치 또는 없음: sentences={}, items={}",
                provider, sentences == null ? null : sentences.size(), expected);
        return null;
    }

    // 로그용: 긴 텍스트는 앞부분만 남긴다
    private static String preview(String text) {
        if (text == null) return "null";
        return text.length() > LOG_PREVIEW_LEN ? text.substring(0, LOG_PREVIEW_LEN) + "..." : text;
    }
}
