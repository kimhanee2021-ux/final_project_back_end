package org.ict.final_project_back_end.domain.library.service;

import org.ict.final_project_back_end.domain.game.entity.GamePriceKrw;
import org.ict.final_project_back_end.domain.game.repository.GamePriceKrwRepository;
import org.ict.final_project_back_end.domain.game.service.GamePriceKrwService;
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
  private final GamePriceKrwService gamePriceKrwService;
  private final GamePriceKrwRepository gamePriceKrwRepository;
  private final RestClient restClient = RestClient.create();
  private final String steamApiKey;

  public LibraryServiceImpl(UserRepository userRepository,
                            LibraryGameRepository libraryGameRepository,
                            GamePriceKrwService gamePriceKrwService,
                            GamePriceKrwRepository gamePriceKrwRepository,
                            @Value("${steam.api-key}") String steamApiKey) {
    this.userRepository = userRepository;
    this.libraryGameRepository = libraryGameRepository;
    this.gamePriceKrwService = gamePriceKrwService;
    this.gamePriceKrwRepository = gamePriceKrwRepository;
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

  // 가격 캐시는 소비 분석과 같은 game_prices_krw 하나를 쓴다. GamePriceKrwService가 가격이 없거나 하루 지난
  // 게임만 100개씩 묶어 스팀에 묻고 저장하므로, 여기서는 그 결과를 응답 형식으로 바꾸기만 한다.
  // 스팀 호출이 섞여 있어 트랜잭션을 걸지 않는다(외부 호출 동안 DB 연결을 붙잡지 않도록).
  @Override
  public List<LibraryGamePriceResponse> getLibraryGamePrices(Long userId) {
    List<LibraryGame> libraryGames = libraryGameRepository.findAllByUserIdOrderByNameAsc(userId);
    if (libraryGames.isEmpty()) {
      return List.of();
    }

    List<Long> appIds = libraryGames.stream().map(LibraryGame::getAppId).toList();
    gamePriceKrwService.ensurePrices(appIds);
    Map<Long, GamePriceKrw> pricesByAppId = gamePriceKrwRepository.findAllById(appIds).stream()
        .collect(Collectors.toMap(GamePriceKrw::getAppId, Function.identity()));

    return libraryGames.stream()
        .map(game -> toPriceResponse(game, pricesByAppId.get(game.getAppId())))
        .toList();
  }

  // 무료(FREE)면 isFree=true에 0원, 가격이 있으면(PRICED) 그 가격, 그 외(가격 없음/미판매/조회 실패)는 null
  private LibraryGamePriceResponse toPriceResponse(LibraryGame game, GamePriceKrw price) {
    if (price != null && GamePriceKrw.FREE.equals(price.getStatus())) {
      return new LibraryGamePriceResponse(game.getAppId(), game.getName(), true, 0, 0, 0);
    }
    if (price != null && GamePriceKrw.PRICED.equals(price.getStatus())) {
      return new LibraryGamePriceResponse(game.getAppId(), game.getName(), false,
          price.getInitialKrw(), price.getFinalKrw(), price.getDiscountPercent());
    }
    return new LibraryGamePriceResponse(game.getAppId(), game.getName(), null, null, null, null);
  }
}
