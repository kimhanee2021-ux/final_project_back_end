package org.ict.final_project_back_end.domain.wishsales.service;

import org.ict.final_project_back_end.domain.wishsales.client.ItadClient;
import org.ict.final_project_back_end.domain.wishsales.client.SalesExplainClient;
import org.ict.final_project_back_end.domain.wishsales.dto.WishlistSalesDtos.GameSalesItem;
import org.ict.final_project_back_end.domain.wishsales.dto.WishlistSalesDtos.SaleSummary;
import org.ict.final_project_back_end.domain.wishsales.dto.WishlistSalesDtos.WishlistSalesResponse;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// [추가] 위시리스트 게임들의 세일 요약을 만들고 AI 설명을 붙이는 서비스.
// 처리 순서: 게임별 요약 생성(캐시 우선) -> AI 문장 요청 -> 실패 시 기본 문장으로 대체.
// ITAD 결과는 6시간 동안 메모리에 캐시한다. 실패한 결과는 캐시하지 않는다.
@Service
public class WishlistSalesService {
    private static final long TTL_MS = 6L * 60 * 60 * 1000; // 캐시 유효 시간: 6시간
    private static final int MAX_GAMES = 20;                // 한 번에 처리할 최대 게임 수 (API 호출량 제한)

    private final ItadClient itad;
    private final SalesExplainClient explain;
    // key: 스팀 appid, value: 캐시된 요약과 저장 시각
    private final Map<Long, Cached> cache = new ConcurrentHashMap<>();

    // 캐시 항목: 요약 결과와 저장된 시각(밀리초)
    private record Cached(SaleSummary summary, long savedAt) {}

    public WishlistSalesService(ItadClient itad, SalesExplainClient explain) {
        this.itad = itad;
        this.explain = explain;
    }

    // appIds: 프론트가 보낸 스팀 appid 목록
    public WishlistSalesResponse build(List<Long> appIds) {
        // 중복 제거 후 최대 개수까지만 사용
        List<Long> ids = appIds.stream().distinct().limit(MAX_GAMES).toList();
        OffsetDateTime now = OffsetDateTime.now();

        // 1) 게임마다 세일 요약을 만든다 (캐시가 있으면 재사용)
        List<SaleSummary> summaries = new ArrayList<>();
        for (Long id : ids) summaries.add(summaryOf(id, now));

        // 2) 요약 전체를 AI에 보내 게임별 문장을 받는다. 실패하면 null
        List<String> sentences = explain.explain(summaries);

        // 3) 게임마다 문장을 붙인다. AI 문장이 없으면 기본 문장을 쓴다.
        List<GameSalesItem> items = new ArrayList<>();
        for (int i = 0; i < summaries.size(); i++) {
            SaleSummary s = summaries.get(i);
            String sentence = (sentences != null && i < sentences.size() && sentences.get(i) != null)
                    ? sentences.get(i)
                    : fallbackSentence(s);
            items.add(new GameSalesItem(s, sentence));
        }
        return new WishlistSalesResponse(items);
    }

    // 게임 한 개의 세일 요약을 가져온다. 캐시 확인 -> ITAD 조회 -> 계산 -> 캐시 저장 순서.
    private SaleSummary summaryOf(long appId, OffsetDateTime now) {
        // 캐시가 아직 유효하면 ITAD를 다시 부르지 않는다
        Cached c = cache.get(appId);
        if (c != null && System.currentTimeMillis() - c.savedAt() < TTL_MS) return c.summary();

        // 키가 없으면 ITAD 호출 자체를 하지 않고 이유만 돌려준다
        if (!itad.isConfigured()) {
            return SaleSummary.insufficient(appId, "ITAD_API_KEY가 설정되지 않았어요");
        }
        try {
            // ITAD 게임 ID 조회. 찾지 못하면 데이터 부족으로 처리
            Optional<String> itadId = itad.lookupId(appId);
            if (itadId.isEmpty()) return SaleSummary.insufficient(appId, "ITAD에서 게임을 찾지 못했어요");

            // 가격 이력을 받아서 요약 계산
            SaleSummary s = SaleSummaryCalculator.summarize(appId, itad.history(itadId.get()), now);
            // 성공한 결과만 캐시에 저장
            cache.put(appId, new Cached(s, System.currentTimeMillis()));
            return s;
        } catch (Exception e) {
            // 외부 API 오류는 사용자에게 간단히 알리고, 이 결과는 캐시하지 않는다 (다음 요청에서 재시도)
            return SaleSummary.insufficient(appId, "가격 기록을 가져오지 못했어요");
        }
    }

    // AI 호출이 안 될 때 쓰는 기본 문장. 숫자는 계산 결과를 그대로 넣는다.
    private String fallbackSentence(SaleSummary s) {
        if (!"OK".equals(s.status())) return "데이터가 부족해요: " + s.reason();
        // 마지막 세일 정보가 없으면 그 부분은 문장에서 뺀다
        String last = s.lastSaleMonthsAgo() == null ? "" : ", 가장 최근 세일은 " + s.lastSaleMonthsAgo() + "개월 전이에요";
        return "최근 " + s.recordMonths() + "개월 기록에서 세일이 " + s.saleCount()
                + "번 있었고 평균 " + s.avgCut() + "% 할인했어요" + last + ".";
    }
}
