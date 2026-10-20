package org.ict.final_project_back_end.domain.analysis.dto;

import java.time.LocalDate;
import java.util.List;

// 로그인 사용자의 스팀 보유 게임 기준 소비 분석 데이터.
// 집계(합계/장르 비율/시간당 비용/월별 등)는 기간 필터와 함께 프론트에서 계산하므로 여기서는 원본 값만 내려준다.
// priceBasis: PURCHASE_HISTORY(가져온 구매내역 기준) | KRW_LIST_PRICE(현재 한국 정가 추정)
public record SpendingAnalysisResponse(
    LocalDate asOf,
    String priceBasis,
    List<SpendingGameResponse> games,
    boolean hasPurchaseHistory,
    // 게임별로 나눌 수 없는 실제 지출(여러 게임이 섞인 번들, 연결 못 한 상품, 선물 구매, 보유하지 않은 게임) 합계
    int unallocatedKrw,
    // 할인으로 아낀 금액 합계 (원래 가격 - 낸 금액, 구매일 환율)
    int savedKrw,
    // 원화로 바꾸지 못해 집계에서 뺀 거래 수 (지원하지 않는 통화)
    int unconvertedCount,
    // 게임에 연결하지 못한 상품 수
    int unmatchedItemCount,
    // 월별 지출 차트·기간 필터용 거래 목록 (최근 거래 먼저, 환불은 음수)
    List<PurchaseView> transactions
) {

  public record PurchaseView(LocalDate purchasedAt, String type, int amountKrw, Long appId, List<String> items,
                             int savedKrw) {
  }
}
