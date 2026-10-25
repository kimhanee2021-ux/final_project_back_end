package org.ict.final_project_back_end.domain.search.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

// 검색 모델 서버 응답. items는 점수가 높은 순이다.
// ignoredTags: 데이터에 없어서 무시한 태그, similarToAppId: similar_to로 찾은 게임(못 찾았거나 요청에 없으면 null)
@JsonIgnoreProperties(ignoreUnknown = true)
public record SearchModelResult(
    List<Item> items,
    @JsonProperty("ignored_tags") List<String> ignoredTags,
    @JsonProperty("similar_to_app_id") Long similarToAppId
) {
  public record Item(@JsonProperty("app_id") Long appId, double score) {
  }
}
