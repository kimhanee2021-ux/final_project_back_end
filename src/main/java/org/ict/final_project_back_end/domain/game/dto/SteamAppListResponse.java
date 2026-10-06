package org.ict.final_project_back_end.domain.game.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

// 스팀 IStoreService/GetAppList/v1 응답 원본 구조. 스팀 JSON 키가 snake_case라 필드마다 매핑한다.
// have_more_results가 true이면 last_appid를 다음 요청의 시작점으로 넘기면 된다.
public record SteamAppListResponse(Body response) {

  public record Body(
      List<App> apps,
      @JsonProperty("have_more_results") Boolean haveMoreResults,
      @JsonProperty("last_appid") Long lastAppId
  ) {
  }

  public record App(Long appid, String name) {
  }
}
