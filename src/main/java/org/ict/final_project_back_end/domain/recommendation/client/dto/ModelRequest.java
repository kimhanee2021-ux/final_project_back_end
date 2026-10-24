package org.ict.final_project_back_end.domain.recommendation.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

// 추천 모델 서버(POST /internal/v1/recommendations) 요청. 모델 서버 JSON 키가 snake_case라 필드마다 매핑한다.
public record ModelRequest(
    @JsonProperty("played_games") List<PlayedGame> playedGames,
    @JsonProperty("exclude_app_ids") List<Long> excludeAppIds,
    int limit,
    @JsonProperty("new_slots") int newSlots
) {
  // 플레이 시간은 시간 단위다 (스팀 원본은 분)
  public record PlayedGame(@JsonProperty("app_id") Long appId, @JsonProperty("playtime_hours") double playtimeHours) {
  }
}
