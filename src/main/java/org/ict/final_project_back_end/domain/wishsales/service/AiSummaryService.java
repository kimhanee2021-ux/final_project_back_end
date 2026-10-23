package org.ict.final_project_back_end.domain.wishsales.service;

import org.ict.final_project_back_end.domain.wishsales.client.AdviceSentenceClient;
import org.ict.final_project_back_end.domain.wishsales.client.AdviceSentenceClient.BatchResult;
import org.ict.final_project_back_end.domain.wishsales.dto.AiSummaryDtos.AiSummaryItem;
import org.ict.final_project_back_end.domain.wishsales.dto.AiSummaryDtos.AiSummaryResponse;
import org.ict.final_project_back_end.domain.wishsales.dto.BuyAdviceDtos.BuyAdviceResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

@Slf4j

// [추가] AI 요약 서비스. 키를 여러 명이 같이 쓰고 30초에 한 번만 호출할 수 있는 조건에 맞춘다.
// 1) 한 요청에 모든 게임을 묶어서 Claude를 한 번만 호출한다 (게임마다 호출하지 않음)
// 2) 30초 안에 또 호출하면 AI를 부르지 않고 규칙 문장을 돌려준다 (전역 쿨다운)
// 3) AI가 쓴 문장은 6시간 캐시해서 같은 게임을 다시 부르지 않는다 (규칙 문장은 캐시하지 않음)
// 4) AI 호출 성공 시 전체 요약(overall)을 message에 담아 프론트 상단 카드/알림에 쓴다
@Service
public class AiSummaryService {
    private static final long TTL_MS = 6L * 60 * 60 * 1000;   // AI 문장 캐시 유효 시간
    private static final long COOLDOWN_MS = 30_000;            // Claude 호출 최소 간격 (30초)
    private static final int MAX_GAMES = 20;                   // 한 번에 요약할 최대 게임 수

    private final BuyAdviceService advice;
    private final AdviceSentenceClient ai;

    // 캐시: appid -> AI 문장과 저장 시각
    private final Map<Long, Cached> cache = new ConcurrentHashMap<>();
    // 마지막으로 Claude를 호출한 시각 (전역, 여러 명이 같이 쓰는 키라서 모두에게 적용)
    private volatile long lastAiCallAt = 0;

    private record Cached(String sentence, long savedAt) {}

    public AiSummaryService(BuyAdviceService advice, AdviceSentenceClient ai) {
        this.advice = advice;
        this.ai = ai;
    }

    public AiSummaryResponse summarize(List<Long> appIds) {
        log.info("ai-summary 호출: appIds={}, configured={}", appIds.size(), ai.isConfigured());
        List<Long> ids = appIds.stream().distinct().limit(MAX_GAMES).toList();
        Map<Long, AiSummaryItem> result = new HashMap<>();
        List<BuyAdviceResponse> needAi = new ArrayList<>();

        // 1) 캐시가 살아 있는 게임은 AI 문장을 그대로 쓴다
        for (Long id : ids) {
            Cached c = cache.get(id);
            if (c != null && System.currentTimeMillis() - c.savedAt() < TTL_MS) {
                result.put(id, new AiSummaryItem(id, stripVerdictPrefix(c.sentence()), "AI"));
                continue;
            }
            // 2) 나머지는 판단을 받아서 모두 AI에 보낸다.
            //    판단이 안 되는 게임(신작, 기록 부족)도 AI가 이유를 자연스러운 문장으로 설명한다.
            BuyAdviceResponse r = advice.advise(id);
            needAi.add(r);
        }

        // 3) 쿨다운 확인: 30초 안이면 Claude를 부르지 않는다
        boolean cooldown = System.currentTimeMillis() - lastAiCallAt < COOLDOWN_MS;
        BatchResult batch = null;
        if (!needAi.isEmpty() && !cooldown && ai.isConfigured()) {
            lastAiCallAt = System.currentTimeMillis();   // 실패해도 호출 시각은 기록 (재시도 폭주 방지)
            batch = ai.explainBatch(needAi);
        }

        // 4) 결과 정리: AI 문장이 왔으면 AI, 아니면 규칙 문장
        List<String> sentences = batch != null ? batch.sentences() : null;
        for (int i = 0; i < needAi.size(); i++) {
            BuyAdviceResponse r = needAi.get(i);
            if (sentences != null && i < sentences.size()
                    && sentences.get(i) != null && !sentences.get(i).isBlank()) {
                String s = stripVerdictPrefix(sentences.get(i));
                cache.put(r.appId(), new Cached(s, System.currentTimeMillis()));
                result.put(r.appId(), new AiSummaryItem(r.appId(), s, "AI"));
            } else {
                result.put(r.appId(), new AiSummaryItem(r.appId(), r.sentence(), "RULE"));
            }
        }

        // 5) 요청한 순서대로 돌려준다
        List<AiSummaryItem> items = ids.stream().map(result::get).toList();

        // 6) message: AI 전체 요약이 있으면 그걸 쓰고, 쿨다운이면 안내 문구
        String message;
        if (cooldown) {
            message = "AI 호출은 30초에 한 번만 가능해서 기본 문장을 보여 드려요";
        } else if (batch != null && batch.overall() != null && !batch.overall().isBlank()) {
            message = batch.overall();
        } else {
            message = null;
        }

        return new AiSummaryResponse(items, cooldown, message);
    }

    // [추가] AI가 문장 앞에 판단 코드(BUY:, WAIT:, UNKNOWN: 등)를 붙이는 경우가 있어서 떼어낸다
    private static final java.util.regex.Pattern VERDICT_PREFIX =
            java.util.regex.Pattern.compile("^\\s*(BUY|WAIT|UNKNOWN|NO_WINDOW|INSUFFICIENT|NEW)\\s*[:：,，\\-–—]\\s*", java.util.regex.Pattern.CASE_INSENSITIVE);

    static String stripVerdictPrefix(String s) {
        if (s == null) return null;
        return VERDICT_PREFIX.matcher(s).replaceFirst("");
    }
}