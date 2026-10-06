package org.ict.final_project_back_end.domain.library.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

// 스팀 IPlayerService/GetOwnedGames 응답 원본 구조. 스팀 JSON 키가 snake_case라 필드마다 매핑한다.
// 프로필이 비공개이면 response 안에 games 키 자체가 없다.
public record SteamOwnedGamesResponse(Body response) {

  public record Body(
      @JsonProperty("game_count") Integer gameCount,
      List<Game> games
  ) {
  }

  public record Game(
      Long appid,
      String name,
      @JsonProperty("playtime_forever") Integer playtimeForever,
      @JsonProperty("playtime_2weeks") Integer playtime2Weeks,
      @JsonProperty("img_icon_url") String imgIconUrl,
      @JsonProperty("rtime_last_played") Long rtimeLastPlayed
  ) {
  }
}
