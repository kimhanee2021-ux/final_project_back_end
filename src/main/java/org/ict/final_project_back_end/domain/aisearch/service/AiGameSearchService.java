package org.ict.final_project_back_end.domain.aisearch.service;

import org.ict.final_project_back_end.domain.aisearch.dto.AiGameSearchResponse;

// 사용자가 문장으로 설명한 게임을 찾는다: GPT로 영어 키워드를 뽑고 -> FastAPI 검색 모델로 찾고 -> 게임 정보를 붙인다.
public interface AiGameSearchService {

  int DEFAULT_LIMIT = 12;
  int MAX_LIMIT = 30;
  int MAX_QUERY_LENGTH = 200;

  AiGameSearchResponse search(String query, int limit);
}
