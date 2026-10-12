package org.ict.final_project_back_end.domain.analysis.service;

import org.ict.final_project_back_end.domain.analysis.dto.SpendingAnalysisResponse;

// 스팀 보유 게임 + 게임 정보 + 원화 가격을 합쳐 개인 소비 분석 데이터를 만든다.
public interface SpendingAnalysisService {

  // DB에 저장된 보유 게임 기준으로 만든다. 최신 플레이 시간이 필요하면 프론트가 먼저 라이브러리 동기화를 호출한다.
  SpendingAnalysisResponse getMySpending(Long userId);
}
