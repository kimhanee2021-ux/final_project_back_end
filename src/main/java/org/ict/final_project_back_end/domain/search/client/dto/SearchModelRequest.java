package org.ict.final_project_back_end.domain.search.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.ict.final_project_back_end.domain.search.client.SearchKeywords;

import java.util.List;

// 검색 모델 서버(POST /internal/v1/search) 요청. 모델 서버 JSON 키가 snake_case라 필드마다 매핑한다.
public record SearchModelRequest(
    List<String> tags,
    @JsonProperty("exclude_tags") List<String> excludeTags,
    String description,
    @JsonProperty("similar_to") String similarTo,
    int limit
) {
  public static SearchModelRequest of(SearchKeywords keywords, int limit) {
    return new SearchModelRequest(keywords.tags(), keywords.excludeTags(), keywords.description(), keywords.similarTo(), limit);
  }
}
