package org.ict.final_project_back_end.domain.aisearch.client;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

// 영어 키워드로 비슷한 게임을 찾는 검색 모델. 지금 구현은 FastAPI 검색 모델(FastApiGameSearchModelClient)이다.
public interface GameSearchModelClient {

  // 쓸 수 있는 키워드가 없으면 AiSearchException.notUnderstood, 서버 장애·이상한 응답은 unavailable
  Response search(SearchKeywords keywords, int limit);

  // 검색 모델 요청/응답 본문 (FastAPI 쪽 이름이 snake_case라 JSON 이름을 따로 지정한다)

  record Request(
      List<String> tags,
      @JsonProperty("exclude_tags") List<String> excludeTags,
      String description,
      @JsonProperty("similar_to") String similarTo,
      int limit) {
  }

  // ignoredTags: 검색 데이터에 없어서 무시된 태그, similarToAppId: similarTo로 찾은 게임 (못 찾았으면 null)
  record Response(
      List<Item> items,
      @JsonProperty("ignored_tags") List<String> ignoredTags,
      @JsonProperty("similar_to_app_id") Long similarToAppId) {
  }

  // score는 한 번의 검색 안에서 순위를 매기는 값일 뿐 확률이 아니다
  record Item(@JsonProperty("app_id") long appId, double score) {
  }
}
