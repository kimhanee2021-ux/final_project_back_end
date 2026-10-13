package org.ict.final_project_back_end.domain.analysis.dto;

import java.time.LocalDateTime;
import java.util.List;

// 소비 분석 화면의 게임 한 건.
// amount는 실제 결제액이 아니라 현재 한국 정가 기준 추정치다 (스팀 공개 API는 구매 금액/날짜를 주지 않음).
// amountSource: ESTIMATED(정가 추정) / FREE(무료 게임, 0원) / UNKNOWN(가격 미확인, amount=null)
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
    LocalDateTime lastPlayedAt
) {
}
