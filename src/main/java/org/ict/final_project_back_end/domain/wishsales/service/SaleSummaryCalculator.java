package org.ict.final_project_back_end.domain.wishsales.service;

import org.ict.final_project_back_end.domain.wishsales.dto.WishlistSalesDtos.PricePoint;
import org.ict.final_project_back_end.domain.wishsales.dto.WishlistSalesDtos.SaleSummary;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

// [추가] 가격 이력으로 세일 요약을 계산하는 클래스.
// 외부 호출이 없는 순수 계산이라서, 입력만 같으면 결과도 항상 같다.
// 계산 기준(세일 할인율, 최소 기록 기간, 최소 세일 횟수)은 아래 상수에 모아 두었다.
public final class SaleSummaryCalculator {
    private SaleSummaryCalculator() {} // 인스턴스를 만들지 않는 유틸리티 클래스

    static final int SALE_CUT = 20;          // 이 할인율 이상이면 "세일 중"으로 본다
    static final int MIN_RECORD_MONTHS = 6;  // 기록 기간이 이보다 짧으면 요약하지 않는다
    static final int MIN_SALE_COUNT = 2;     // 세일 횟수가 이보다 적으면 요약하지 않는다

    // points: 시간순으로 정렬된 가격 이력, now: 기준 시각(마지막 세일 경과 계산에 사용)
    public static SaleSummary summarize(long appId, List<PricePoint> points, OffsetDateTime now) {
        // 기록 자체가 없으면 계산할 것이 없다
        if (points == null || points.isEmpty()) {
            return SaleSummary.insufficient(appId, "가격 기록이 없어요");
        }

        // 첫 기록부터 현재까지 몇 개월인지 계산 (기록 기간 확인용)
        long recordMonths = ChronoUnit.MONTHS.between(points.get(0).at(), now);
        if (recordMonths < MIN_RECORD_MONTHS) {
            return SaleSummary.insufficient(appId, "가격 기록이 6개월 미만이에요");
        }

        int saleStarts = 0;          // 세일이 새로 시작된 횟수 (연속된 세일은 한 번으로 센다)
        boolean inSale = false;      // 직전 기록이 세일 중이었는지
        long sumCut = 0;             // 세일 중 기록들의 할인율 합계 (평균 계산용)
        int saleDays = 0;            // 세일 중인 기록 건수
        int maxCut = 0;              // 세일 중 최대 할인율
        OffsetDateTime lastSaleAt = null; // 마지막으로 세일 기준을 넘은 시각

        for (PricePoint p : points) {
            boolean sale = p.cut() >= SALE_CUT;
            // 직전에는 세일이 아니었는데 지금 세일이면, 세일이 새로 시작된 것
            if (sale && !inSale) saleStarts++;
            inSale = sale;
            if (sale) {
                sumCut += p.cut();
                saleDays++;
                maxCut = Math.max(maxCut, p.cut());
                lastSaleAt = p.at();
            }
        }

        // 세일이 너무 적으면 패턴이라고 말할 수 없으므로 요약하지 않는다
        if (saleStarts < MIN_SALE_COUNT) {
            return SaleSummary.insufficient(appId, "세일 기록이 2번 미만이에요");
        }

        // 세일 중 기록이 없으면 평균은 0으로 둔다 (위에서 세일 2번 이상을 확인했으므로 실제로는 거의 없음)
        int avgCut = saleDays == 0 ? 0 : (int) (sumCut / saleDays);
        // 마지막 세일이 없으면 null, 있으면 지금까지 몇 개월 지났는지 계산
        Long monthsSince = lastSaleAt == null ? null : ChronoUnit.MONTHS.between(lastSaleAt, now);
        return new SaleSummary(appId, "OK", null, recordMonths, saleStarts, avgCut, maxCut, monthsSince);
    }
}
