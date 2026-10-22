package org.ict.final_project_back_end.domain.wishsales.dto;

import java.util.List;

// [추가] 할인 달 전망 API 응답 형식.
// lastYearSaleMonths: 작년에 기준 이상 할인했던 달
// thisYearSaleMonths: 올해 지금까지 확인된 기준 이상 할인 달
// upcoming: 아직 오지 않은 달의 할인 가능성(%)
public final class DiscountOutlookDtos {
    private DiscountOutlookDtos() {} // 모음 클래스라 객체를 만들지 않음

    public record MonthChance(int month, int percent) {}

    public record DiscountOutlookResponse(
            long appId,
            int threshold,
            String status,                 // OK 또는 INSUFFICIENT
            String reason,                 // INSUFFICIENT일 때 이유
            List<Integer> lastYearSaleMonths,
            List<Integer> thisYearSaleMonths,
            List<MonthChance> upcoming
    ) {
        // 계산할 수 없을 때 빈 목록과 이유만 담아 돌려준다
        public static DiscountOutlookResponse insufficient(long appId, int threshold, String reason) {
            return new DiscountOutlookResponse(appId, threshold, "INSUFFICIENT", reason, List.of(), List.of(), List.of());
        }
    }
}
