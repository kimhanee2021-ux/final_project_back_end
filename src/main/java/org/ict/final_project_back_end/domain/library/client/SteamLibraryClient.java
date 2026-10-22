package org.ict.final_project_back_end.domain.library.client;

import static org.ict.final_project_back_end.global.constant.SteamUrls.STEAM_API_BASE_URL;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.ict.final_project_back_end.domain.library.dto.SteamOwnedGamesResponse;
import org.ict.final_project_back_end.global.exception.PrivateSteamLibraryException;
import org.ict.final_project_back_end.global.exception.SteamApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

// 스팀 Web API(IPlayerService/GetOwnedGames)로 사용자의 보유 게임 목록을 가져온다.
@Component
public class SteamLibraryClient {

  private final RestClient restClient = RestClient.create();
  private final String steamApiKey;

  public SteamLibraryClient(@Value("${steam.api-key}") String steamApiKey) {
    this.steamApiKey = steamApiKey;
  }

  // 프로필이나 게임 세부 정보가 비공개라 목록이 비어 오면 PrivateSteamLibraryException을 던진다.
  public List<SteamOwnedGamesResponse.Game> fetchOwnedGames(String steamId) {
    URI uri = UriComponentsBuilder.fromUriString(STEAM_API_BASE_URL + "/IPlayerService/GetOwnedGames/v1/")
        .queryParam("key", steamApiKey)
        .queryParam("steamid", steamId)
        .queryParam("include_appinfo", true)
        .queryParam("include_played_free_games", true)
        .queryParam("format", "json")
        .encode(StandardCharsets.UTF_8)
        .build()
        .toUri();

    SteamOwnedGamesResponse response;
    try {
      response = restClient.get()
          .uri(uri)
          .retrieve()
          .body(SteamOwnedGamesResponse.class);
    } catch (RestClientException e) {
      // 예외 메시지에는 요청 URL(=API 키 포함)이 섞일 수 있으므로 원본 예외는 전달하지 않는다
      throw new SteamApiException();
    }

    if (response == null || response.response() == null || response.response().games() == null) {
      throw new PrivateSteamLibraryException();
    }
    return response.response().games();
  }
}
