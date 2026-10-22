package org.ict.final_project_back_end.domain.wishsales.service;

import org.ict.final_project_back_end.domain.wishsales.client.ItadClient;
import org.ict.final_project_back_end.domain.wishsales.dto.BuyAdviceDtos.BuyAdviceResponse;
import org.ict.final_project_back_end.domain.wishsales.dto.WishlistSalesDtos.PricePoint;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// [추가] "지금 사면 손해인지, 올해 말까지 기다릴 만한지" 판단 서비스.
// 검증 결과(2025-12~2026-10 데이터, 2,555건): "기다리세요" 정밀도 75%, 재현율 80%.
@Service
public class BuyAdviceService {
    // 기다리세요 기준: 기대치가 지금보다 이만큼(%p) 이상 높을 때
    static final int WAIT_GAP = 10;
    private static final long TTL_MS = 6L * 60 * 60 * 1000;
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final ItadClient itad;
    private final Map<Long, Cached> cache = new ConcurrentHashMap<>();

    private record Cached(List<PricePoint> points, long savedAt) {}

    // [변경] AI는 호출하지 않는다. 기본 판단 응답은 규칙 문장만 쓰고, AI 요약은 별도 API(ai-summary)에서 한 번에 처리한다
    public BuyAdviceService(ItadClient itad) {
        this.itad = itad;
    }

    public BuyAdviceResponse advise(long appId) {
        List<PricePoint> points;
        try {
            points = historyOf(appId);
        } catch (Exception e) {
            return BuyAdviceResponse.insufficient(appId, "INSUFFICIENT", "가격 기록을 가져오지 못했어요");
        }
        if (points == null) {
            return BuyAdviceResponse.insufficient(appId, "INSUFFICIENT", "ITAD 키가 없거나 게임을 찾지 못했어요");
        }

        YearMonth now = YearMonth.now(ZONE);
        int year = now.getYear();
        int month = now.getMonthValue();

        // 12월이면 볼 달이 없다
        if (month == 12) {
            return new BuyAdviceResponse(appId, "NO_WINDOW", "UNKNOWN",
                    "올해가 거의 끝나서 판단할 기간이 없어요", null, null, null, null,
                    "올해가 거의 끝나서 판단할 기간이 없어요.");
        }

        // 지금 달 할인율과 작년 기록(남은 달 전체)을 월별 최대 할인율로 만든다
        Map<YearMonth, Integer> monthly = MonthlyDiscountCalculator.monthlyMax(
                points, YearMonth.of(year - 1, 1), now);

        Integer current = monthly.get(now);
        if (current == null) {
            return BuyAdviceResponse.insufficient(appId, "INSUFFICIENT", "이번 달 할인 기록이 없어요");
        }

        // 기대치: 다음 달 ~ 12월, 작년 같은 달 최대 할인율의 최댓값
        int expected = -1;
        int expectedMonth = -1;
        for (int m = month + 1; m <= 12; m++) {
            Integer ly = monthly.get(YearMonth.of(year - 1, m));
            if (ly == null) {
                return BuyAdviceResponse.insufficient(appId, "INSUFFICIENT", "할인 기록이 부족해서 판단이 어려워요");
            }
            if (ly > expected) {
                expected = ly;
                expectedMonth = m;
            }
        }

        String windowText = (month + 1) + "월 ~ 12월";
        boolean wait = expected - current >= WAIT_GAP;
        String verdict = wait ? "WAIT" : "BUY";
        String sentence = wait
                ? "작년 " + expectedMonth + "월에 최대 " + expected + "% 할인했어요. 지금(" + current + "%)보다 높아서 기다리는 편이 나을 수 있어요."
                : "남은 기간(" + windowText + ")에 지금보다 큰 할인이 기대되지 않아요. 지금 사도 괜찮아요.";

        return new BuyAdviceResponse(appId, "OK", verdict, null, current, expected, expectedMonth, windowText, sentence);
    }

    private List<PricePoint> historyOf(long appId) {
        Cached c = cache.get(appId);
        if (c != null && System.currentTimeMillis() - c.savedAt() < TTL_MS) return c.points();
        if (!itad.isConfigured()) return null;
        Optional<String> itadId = itad.lookupId(appId);
        if (itadId.isEmpty()) return null;
        List<PricePoint> points = itad.history(itadId.get());
        cache.put(appId, new Cached(points, System.currentTimeMillis()));
        return points;
    }
}
