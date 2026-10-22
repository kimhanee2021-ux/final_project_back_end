package org.ict.final_project_back_end.domain.wishsales.service;

import org.ict.final_project_back_end.domain.wishsales.dto.DiscountOutlookDtos.DiscountOutlookResponse;
import org.ict.final_project_back_end.domain.wishsales.dto.DiscountOutlookDtos.MonthChance;
import org.ict.final_project_back_end.domain.wishsales.dto.WishlistSalesDtos.PricePoint;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// [추가] 할인 달 전망 서비스.
// 1) ITAD에서 가격 이력 가져오기 (PriceHistoryCache, 6시간 캐시)
// 2) 월별 최대 할인율 계산
// 3) 작년 할인 달, 올해 확인된 할인 달, 남은 달의 확률 만들기
@Service
public class DiscountOutlookService {
    // 기준 할인율 기본 시간대: 한국 시간으로 "올해"를 판단한다
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final PriceHistoryCache history;
    private final DiscountProbabilityModel model;

    public DiscountOutlookService(PriceHistoryCache history, DiscountProbabilityModel model) {
        this.history = history;
        this.model = model;
    }

    public DiscountOutlookResponse outlook(long appId, int threshold) {
        if (!model.supports(threshold)) {
            return DiscountOutlookResponse.insufficient(appId, threshold, "지원하지 않는 기준이에요 (20, 30, 40, 50만 가능)");
        }

        // 가격 이력 가져오기 (실패하면 이유를 돌려준다)
        List<PricePoint> points;
        try {
            points = history.historyOf(appId);
        } catch (Exception e) {
            return DiscountOutlookResponse.insufficient(appId, threshold, "가격 기록을 가져오지 못했어요");
        }
        if (points == null) {
            return DiscountOutlookResponse.insufficient(appId, threshold, "ITAD 키가 없거나 게임을 찾지 못했어요");
        }

        YearMonth now = YearMonth.now(ZONE);
        int lastYear = now.getYear() - 1;

        // 작년 1월 ~ 올해 현재 달까지의 월별 최대 할인율
        Map<YearMonth, Integer> monthly = MonthlyDiscountCalculator.monthlyMax(
                points, YearMonth.of(lastYear, 1), now);

        // 작년에 기준 이상 할인했던 달
        List<Integer> lastYearSale = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            Integer v = monthly.get(YearMonth.of(lastYear, m));
            if (v != null && v >= threshold) lastYearSale.add(m);
        }
        // 작년 기록이 전혀 없으면 계산 근거가 없다
        boolean hasLastYear = false;
        for (int m = 1; m <= 12; m++) {
            if (monthly.containsKey(YearMonth.of(lastYear, m))) { hasLastYear = true; break; }
        }
        if (!hasLastYear) {
            return DiscountOutlookResponse.insufficient(appId, threshold, "작년 기록이 없어서 계산할 수 없어요");
        }

        // 올해 지금 달까지 확인된 기준 이상 할인 달
        List<Integer> thisYearSale = new ArrayList<>();
        for (int m = 1; m <= now.getMonthValue(); m++) {
            Integer v = monthly.get(YearMonth.of(now.getYear(), m));
            if (v != null && v >= threshold) thisYearSale.add(m);
        }

        // 아직 오지 않은 달: 작년 같은 달 기록으로 확률 계산 (작년 기록이 있는 달만)
        List<MonthChance> upcoming = new ArrayList<>();
        for (int m = now.getMonthValue() + 1; m <= 12; m++) {
            Integer ly = monthly.get(YearMonth.of(lastYear, m));
            if (ly != null) {
                upcoming.add(new MonthChance(m, model.percent(threshold, ly, m)));
            }
        }

        return new DiscountOutlookResponse(appId, threshold, "OK", null, lastYearSale, thisYearSale, upcoming);
    }
}
