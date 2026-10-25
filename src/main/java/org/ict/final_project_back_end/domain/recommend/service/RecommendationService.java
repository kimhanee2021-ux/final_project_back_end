package org.ict.final_project_back_end.domain.recommend.service;

import org.ict.final_project_back_end.domain.recommend.dto.RecommendationResponse;

// 로그인한 사용자의 보유 게임 플레이 기록으로 FastAPI 추천 서버에 추천을 받아 게임 정보를 붙인다.
public interface RecommendationService {

  int DEFAULT_LIMIT = 10;
  int MAX_LIMIT = 50;
  int DEFAULT_NEW_SLOTS = 3;

  // limit개 중 newSlots개는 학습에 없던 신작으로 채운다. 이미 보유한 게임은 추천하지 않는다.
  RecommendationResponse recommendForUser(Long userId, int limit, int newSlots);
}
