package org.ict.final_project_back_end.domain.wishsales.client;

import org.ict.final_project_back_end.domain.wishsales.dto.WishlistSalesDtos.SaleSummary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

// [추가] Claude API 호출 클래스.
// 계산된 세일 요약 숫자를 보내고, 게임마다 한국어 한 문장을 받는다.
// AI는 계산을 하지 않고 주어진 숫자만 말하도록 프롬프트에서 제한한다.
// 키가 없거나 호출이 실패하면 null을 돌려주고, 호출하는 쪽이 기본 문장을 쓴다.
@Component
public class SalesExplainClient {
    private static final String URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL = "claude-haiku-5-5";

    private final RestClient http = RestClient.create();
    private final ObjectMapper mapper = new ObjectMapper(); // 요약 객체를 JSON 문자열로 바꾸거나 응답을 읽을 때 사용
    private final String apiKey;

    // ANTHROPIC_API_KEY 환경변수에서 키를 읽는다. 없으면 빈 문자열이 들어온다.
    public SalesExplainClient(@Value("${ANTHROPIC_API_KEY:}") String apiKey) {
        this.apiKey = apiKey;
    }

    // summaries 순서와 같은 길이의 문장 목록을 돌려준다.
    // 실패하면 null을 돌려줘서 기능 전체가 멈추지 않게 한다.
    public List<String> explain(List<SaleSummary> summaries) {
        if (apiKey == null || apiKey.isBlank() || summaries.isEmpty()) return null;
        try {
            // 프롬프트: 숫자는 주어진 값만 쓰고, 출력은 JSON 배열 하나로만 받도록 지시한다.
            String prompt = "아래 JSON은 게임별 세일 요약 숫자입니다. 각 항목마다 한국어 한 문장을 만드세요. "
                    + "숫자는 주어진 값만 쓰고 새 숫자를 만들지 마세요. status가 INSUFFICIENT면 reason을 바탕으로 데이터가 부족하다고 쓰세요. "
                    + "결과는 입력과 같은 순서, 같은 개수의 문자열 JSON 배열만 출력하세요.\n"
                    + mapper.writeValueAsString(summaries);

            // Anthropic Messages API 요청 본문 만들기
            ObjectNode body = mapper.createObjectNode();
            body.put("model", MODEL);
            body.put("max_tokens", 1000);                 // 응답 최대 길이 제한
            ArrayNode messages = body.putArray("messages");
            ObjectNode msg = messages.addObject();
            msg.put("role", "user");
            msg.put("content", prompt);

            // 요청 전송. 인증은 x-api-key 헤더, 버전은 anthropic-version 헤더로 지정한다.
            JsonNode res = http.post()
                    .uri(URL)
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            // 응답 텍스트는 content[0].text에 들어 있다
            String text = res.path("content").get(0).path("text").asText();
            // 모델이 낸 JSON 배열 문자열을 List<String>으로 변환
            return mapper.readValue(text, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            // 네트워크 오류, 응답 형식 오류 등은 모두 기본 문장으로 대체한다
            return null;
        }
    }
}
