package org.ict.final_project_back_end.domain.recommend.client;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

// 플레이 이력으로 게임을 추천하는 모델. 지금 구현은 FastAPI 추천 서버(FastApiRecommendModelClient)다.
// 누구의 어떤 게임을 보낼지는 RecommendationServiceImpl이 정한다.
public interface RecommendModelClient {

  // 모델이 아는 게임이 이력에 하나도 없으면 RecommendException.noPlayedGames,
  // 서버에 연결할 수 없으면 unavailable, 그 밖의 오류·이상한 응답은 invalidResponse
  Response recommend(Request request);

  // 추천 서버 요청/응답 본문 (FastAPI 쪽 이름이 snake_case라 JSON 이름을 따로 지정한다)

  // playedGames는 오래된 순 -> 최근 순. 모델은 최근 이력부터 쓴다.
  record Request(
      @JsonProperty("played_games") List<PlayedGame> playedGames,
      @JsonProperty("exclude_app_ids") List<Long> excludeAppIds,
      int limit,
      @JsonProperty("new_slots") int newSlots) {
  }

  record PlayedGame(@JsonProperty("app_id") long appId, @JsonProperty("playtime_hours") double playtimeHours) {
  }

  record Response(
      List<Item> items,
      @JsonProperty("used_app_ids") List<Long> usedAppIds,
      @JsonProperty("ignored_app_ids") List<Long> ignoredAppIds) {
  }

  // score는 한 번의 추천 안에서 순위를 매기는 값일 뿐 확률이 아니다. source: main = 기본, new = 학습에 없던 신작
  record Item(@JsonProperty("app_id") long appId, double score, String source) {
  }
}
