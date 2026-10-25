package org.ict.final_project_back_end.domain.search.client;

import org.ict.final_project_back_end.domain.search.client.dto.SearchModelRequest;
import org.ict.final_project_back_end.domain.search.client.dto.SearchModelResult;

// 키워드로 게임 순위를 계산하는 검색 모델. 서비스는 구현체(FastAPI)를 모른다.
public interface SearchModelClient {

  // 호출이 실패하면(연결 불가, 시간 초과, 서버 오류) SearchException
  SearchModelResult search(SearchModelRequest request);
}
