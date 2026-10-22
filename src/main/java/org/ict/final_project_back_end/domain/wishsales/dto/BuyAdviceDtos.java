package org.ict.final_project_back_end.domain.wishsales.dto;

// [추가] "지금 살지, 올해 말까지 기다릴지" 판단 결과 형식.
// 규칙: 올해 남은 달(다음 달 ~ 12월) 중 작년 같은 달 최대 할인율의 최댓값(기대치)이
//       지금 할인율보다 10%p 이상 높으면 WAIT, 아니면 BUY.
public final class BuyAdviceDtos {
    private BuyAdviceDtos() {} // 모음 클래스라 객체를 만들지 않음

    public record BuyAdviceResponse(
            long appId,
            String status,            // OK, INSUFFICIENT(데이터 부족), NO_WINDOW(12월이라 기간 없음)
            String verdict,           // WAIT(기다리세요), BUY(지금 사도 괜찮아요), UNKNOWN(판단 어려움)
            String reason,            // 판단이 안 될 때 이유
            Integer currentPercent,   // 지금 할인율 (이번 달 최대)
            Integer expectedPercent,  // 기대 할인율 (남은 달 중 작년 같은 달 최대의 최댓값)
            Integer expectedMonth,    // 기대 할인율이 나온 달 (1~12)
            String windowText,        // 판단 기간 표시. 예: "11월 ~ 12월"
            String sentence           // 사용자에게 보여 줄 문장 (지금은 규칙 문장, 나중에 AI 문장으로 교체)
    ) {
        public static BuyAdviceResponse insufficient(long appId, String status, String reason) {
            return new BuyAdviceResponse(appId, status, "UNKNOWN", reason, null, null, null, null, reason);
        }
    }
}
