package org.ict.final_project_back_end.domain.analysis.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// 소비 분석 화면의 게임 한 건.
// amountSource:
//   CONFIRMED  가져온 구매내역의 실제 결제액(구매일 환율로 원화 환산, DLC 포함). refund/purchasedAt도 채워짐
//   NO_RECORD  구매내역은 가져왔는데 이 게임의 결제 기록이 없음(선물·키 등록·무료 배포 등) -> amount=null, listPrice만 참고
//   ESTIMATED  구매내역을 아직 안 가져와서 현재 한국 정가로 추정
//   FREE       무료 게임(0원)
//   UNKNOWN    가격을 알 수 없음(판매 종료·한국 미판매) -> amount=null
public record SpendingGameResponse(
    Long appId,
    String name,
    String genre,
    List<String> genres,
    String thumbnailUrl,
    Integer amount,
    String amountSource,
    Integer currentPrice,
    Integer discountPercent,
    double hours,
    double recentHours,
    LocalDateTime lastPlayedAt,
    Integer listPrice,
    int refund,
    LocalDate purchasedAt,
    int dlcCount,
    int savedKrw
) {
}
