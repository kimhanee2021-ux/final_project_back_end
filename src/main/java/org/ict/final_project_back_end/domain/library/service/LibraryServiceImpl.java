package org.ict.final_project_back_end.domain.library.service;

import org.ict.final_project_back_end.domain.game.entity.Game;
import org.ict.final_project_back_end.domain.game.repository.GameRepository;
import org.ict.final_project_back_end.domain.library.dto.LibraryGamePriceResponse;
import org.ict.final_project_back_end.domain.library.dto.LibraryGameResponse;
import org.ict.final_project_back_end.domain.library.dto.SteamOwnedGamesResponse;
import org.ict.final_project_back_end.domain.library.entity.LibraryGame;
import org.ict.final_project_back_end.domain.library.repository.LibraryGameRepository;
import org.ict.final_project_back_end.domain.user.entity.User;
import org.ict.final_project_back_end.domain.user.repository.UserRepository;
import org.ict.final_project_back_end.global.exception.PrivateSteamLibraryException;
import org.ict.final_project_back_end.global.exception.SteamApiException;
import org.ict.final_project_back_end.global.exception.SteamNotLinkedException;
import org.ict.final_project_back_end.global.exception.UserNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.ict.final_project_back_end.global.constant.SteamUrls.STEAM_API_BASE_URL;

@Service
public class LibraryServiceImpl implements LibraryService {

  // 스팀 게임 아이콘 경로 형식: .../apps/{appid}/{img_icon_url}.jpg
  private static final String STEAM_ICON_URL_FORMAT = "https://media.steampowered.com/steamcommunity/public/images/apps/%d/%s.jpg";

  private final UserRepository userRepository;
  private final LibraryGameRepository libraryGameRepository;
  private final GameRepository gameRepository;
  private final RestClient restClient = RestClient.create();
  private final String steamApiKey;

  public LibraryServiceImpl(UserRepository userRepository,
                            LibraryGameRepository libraryGameRepository,
                            GameRepository gameRepository,
                            @Value("${steam.api-key}") String steamApiKey) {
    this.userRepository = userRepository;
    this.libraryGameRepository = libraryGameRepository;
    this.gameRepository = gameRepository;
    this.steamApiKey = steamApiKey;
  }

  @Override
  @Transactional
  public List<LibraryGameResponse> syncSteamLibrary(Long userId) {
    User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    if (user.getSteamId() == null) {
      throw new SteamNotLinkedException();
    }

    List<SteamOwnedGamesResponse.Game> steamGames = fetchOwnedGames(user.getSteamId());

    // appId 기준으로 기존 행을 찾아 갱신하고, 없는 게임만 새로 만든다 (중복 저장 방지)
    Map<Long, LibraryGame> existingByAppId = libraryGameRepository.findAllByUserId(userId).stream()
        .collect(Collectors.toMap(LibraryGame::getAppId, Function.identity()));

    List<LibraryGame> toSave = steamGames.stream()
        .map(steamGame -> upsert(user, existingByAppId.get(steamGame.appid()), steamGame))
        .toList();
    libraryGameRepository.saveAll(toSave);

    return libraryGameRepository.findAllByUserIdOrderByNameAsc(userId).stream()
        .map(LibraryGameResponse::from)
        .toList();
  }

  private LibraryGame upsert(User user, LibraryGame existing, SteamOwnedGamesResponse.Game steamGame) {
    String iconUrl = steamGame.imgIconUrl() == null
        ? null
        : STEAM_ICON_URL_FORMAT.formatted(steamGame.appid(), steamGame.imgIconUrl());
    // 스팀은 한 번도 플레이하지 않은 게임의 마지막 플레이 시각을 0으로 내려준다
    LocalDateTime lastPlayedAt = steamGame.rtimeLastPlayed() == null || steamGame.rtimeLastPlayed() == 0
        ? null
        : LocalDateTime.ofInstant(Instant.ofEpochSecond(steamGame.rtimeLastPlayed()), ZoneId.systemDefault());
    Integer playtimeForever = valueOrZero(steamGame.playtimeForever());
    Integer playtime2Weeks = valueOrZero(steamGame.playtime2Weeks());

    if (existing != null) {
      existing.updateFromSteam(steamGame.name(), playtimeForever, playtime2Weeks, lastPlayedAt, iconUrl);
      return existing;
    }
    return LibraryGame.builder()
        .user(user)
        .appId(steamGame.appid())
        .name(steamGame.name())
        .playtimeForever(playtimeForever)
        .playtime2Weeks(playtime2Weeks)
        .lastPlayedAt(lastPlayedAt)
        .iconUrl(iconUrl)
        .build();
  }

  private List<SteamOwnedGamesResponse.Game> fetchOwnedGames(String steamId) {
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

  private Integer valueOrZero(Integer value) {
    return value == null ? 0 : value;
  }

  // 가격은 games 테이블의 원화 정가(original_price)를 그대로 쓴다. 외부 호출이 없어 트랜잭션은 걸지 않는다.
  // games에 없는 게임이나 가격이 비어 있는 게임은 가격 필드를 null로 내려준다.
  @Override
  public List<LibraryGamePriceResponse> getLibraryGamePrices(Long userId) {
    List<LibraryGame> libraryGames = libraryGameRepository.findAllByUserIdOrderByNameAsc(userId);
    if (libraryGames.isEmpty()) {
      return List.of();
    }

    List<Long> appIds = libraryGames.stream().map(LibraryGame::getAppId).toList();
    Map<Long, Game> gamesByAppId = gameRepository.findAllById(appIds).stream()
        .collect(Collectors.toMap(Game::getAppId, Function.identity()));

    return libraryGames.stream()
        .map(game -> toPriceResponse(game, gamesByAppId.get(game.getAppId())))
        .toList();
  }

  // 정가 0원이면 isFree=true, 정가가 있으면 그 가격(현재가=정가, 할인율 0), 그 외(없음/미등록)는 null
  private LibraryGamePriceResponse toPriceResponse(LibraryGame library, Game game) {
    if (game == null || game.getOriginalPrice() == null) {
      return new LibraryGamePriceResponse(library.getAppId(), library.getName(), null, null, null, null);
    }
    int price = game.getOriginalPrice().intValue();
    return new LibraryGamePriceResponse(library.getAppId(), library.getName(), price == 0, price, price, 0);
  }
}
