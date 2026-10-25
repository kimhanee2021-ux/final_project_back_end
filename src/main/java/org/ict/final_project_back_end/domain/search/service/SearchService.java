package org.ict.final_project_back_end.domain.search.service;

import org.ict.final_project_back_end.domain.search.dto.NaturalSearchResponse;

public interface SearchService {

  // 한국어 검색어를 AI가 키워드로 바꾸고, 검색 모델이 순위를 매긴 게임을 games 정보와 함께 돌려준다.
  // 횟수 제한을 넘으면 SearchException(429), 검색 모델 서버 호출이 실패하면 SearchException(502)
  NaturalSearchResponse search(Long userId, String query, int limit);
}
