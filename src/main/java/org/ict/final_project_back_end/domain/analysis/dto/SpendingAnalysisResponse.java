package org.ict.final_project_back_end.domain.analysis.dto;

import java.time.LocalDate;
import java.util.List;

// 로그인 사용자의 스팀 보유 게임 기준 소비 분석 데이터.
// 집계(합계/장르 비율/시간당 비용 등)는 기간 필터와 함께 프론트에서 계산하므로 여기서는 게임별 원본 값만 내려준다.
public record SpendingAnalysisResponse(
    LocalDate asOf,
    String priceBasis,
    List<SpendingGameResponse> games
) {
}
