package org.ict.final_project_back_end.domain.recommendation.client;

import org.ict.final_project_back_end.domain.recommendation.client.dto.ModelRequest;
import org.ict.final_project_back_end.domain.recommendation.client.dto.ModelResult;

// 플레이 이력으로 게임 순위를 계산하는 추천 모델. 서비스는 구현체(FastAPI)를 모른다.
public interface RecommendationModelClient {

  // 모델이 아는 플레이한 게임이 없으면 NoPlayedGamesException, 호출이 실패하면 RecommendationServerException
  ModelResult recommend(ModelRequest request);
}
