package org.ict.final_project_back_end.domain.recommendation.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

// 추천 모델 서버 응답. items는 점수가 높은 순이다.
// usedAppIds: 취향 계산에 쓴 게임, ignoredAppIds: 모델이 몰라서 취향 계산에서 뺀 게임
@JsonIgnoreProperties(ignoreUnknown = true)
public record ModelResult(
    List<Item> items,
    @JsonProperty("used_app_ids") List<Long> usedAppIds,
    @JsonProperty("ignored_app_ids") List<Long> ignoredAppIds
) {
  public record Item(@JsonProperty("app_id") Long appId, double score, String source) {
  }
}
