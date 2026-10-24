package org.ict.final_project_back_end.domain.recommendation.service;

import org.ict.final_project_back_end.domain.recommendation.dto.RecommendationResponse;

// 사용자의 보유 게임을 바탕으로 게임을 추천하는 서비스.
public interface RecommendationService {

  // 저장된 추천이 있고 아직 유효하면 그대로, 아니면 모델 서버로 새로 계산해 저장한 뒤 limit개를 순위대로 반환한다.
  // 플레이한 게임이 없으면 NoPlayedGamesException.
  RecommendationResponse getRecommendations(Long userId, int limit);
}
