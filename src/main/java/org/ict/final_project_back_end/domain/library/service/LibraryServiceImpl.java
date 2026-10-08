package org.ict.final_project_back_end.domain.library.service;

import org.ict.final_project_back_end.domain.library.dto.LibraryGamePriceResponse;
import org.ict.final_project_back_end.domain.library.dto.LibraryGameResponse;
import org.ict.final_project_back_end.domain.library.dto.SteamAppDetailsResponse;
import org.ict.final_project_back_end.domain.library.dto.SteamOwnedGamesResponse;
import org.ict.final_project_back_end.domain.library.entity.LibraryGame;
import org.ict.final_project_back_end.domain.library.entity.LibraryGamePrice;
import org.ict.final_project_back_end.domain.library.repository.LibraryGamePriceRepository;
import org.ict.final_project_back_end.domain.library.repository.LibraryGameRepository;
import org.ict.final_project_back_end.domain.user.entity.User;
import org.ict.final_project_back_end.domain.user.repository.UserRepository;
import org.ict.final_project_back_end.global.exception.PrivateSteamLibraryException;
import org.ict.final_project_back_end.global.exception.SteamApiException;
import org.ict.final_project_back_end.global.exception.SteamNotLinkedException;
import org.ict.final_project_back_end.global.exception.UserNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.ict.final_project_back_end.global.constant.SteamUrls.STEAM_API_BASE_URL;
import static org.ict.final_project_back_end.global.constant.SteamUrls.STEAM_STORE_BASE_URL;

@Service
public class LibraryServiceImpl implements LibraryService {

  // 스팀 게임 아이콘 경로 형식: .../apps/{appid}/{img_icon_url}.jpg
  private static final String STEAM_ICON_URL_FORMAT = "https://media.steampowered.com/steamcommunity/public/images/apps/%d/%s.jpg";
  // 가격 캐시 유효 기간. 이 기간이 지난 게임만 스팀 스토어에 다시 조회한다.
  private static final Duration PRICE_CACHE_TTL = Duration.ofDays(1);

  private final UserRepository userRepository;
  private final LibraryGameRepository libraryGameRepository;
  private final LibraryGamePriceRepository libraryGamePriceRepository;
  private final RestClient restClient = RestClient.create();
  private final String steamApiKey;

  public LibraryServiceImpl(UserRepository userRepository,
                            LibraryGameRepository libraryGameRepository,
                            LibraryGamePriceRepository libraryGamePriceRepository,
                            @Value("${steam.api-key}") String steamApiKey) {
    this.userRepository = userRepository;
    this.libraryGameRepository = libraryGameRepository;
    this.libraryGamePriceRepository = libraryGamePriceRepository;
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

  @Override
  @Transactional
  public List<LibraryGamePriceResponse> getLibraryGamePrices(Long userId) {
    List<LibraryGame> libraryGames = libraryGameRepository.findAllByUserIdOrderByNameAsc(userId);
    if (libraryGames.isEmpty()) {
      return List.of();
    }

    List<Long> appIds = libraryGames.stream().map(LibraryGame::getAppId).toList();
    Map<Long, LibraryGamePrice> pricesByAppId = libraryGamePriceRepository.findAllByAppIdIn(appIds).stream()
        .collect(Collectors.toMap(LibraryGamePrice::getAppId, Function.identity()));

    LocalDateTime now = LocalDateTime.now();
    LocalDateTime staleBefore = now.minus(PRICE_CACHE_TTL);
    List<LibraryGamePrice> toSave = new ArrayList<>();
    for (Long appId : appIds) {
      LibraryGamePrice cached = pricesByAppId.get(appId);
      if (cached != null && cached.getCheckedAt().isAfter(staleBefore)) {
        continue;
      }
      LibraryGamePrice refreshed = fetchAndUpdatePrice(appId, cached, now);
      if (refreshed != null) {
        toSave.add(refreshed);
        pricesByAppId.put(appId, refreshed);
      }
    }
    if (!toSave.isEmpty()) {
      libraryGamePriceRepository.saveAll(toSave);
    }

    return libraryGames.stream()
        .map(game -> toPriceResponse(game, pricesByAppId.get(game.getAppId())))
        .toList();
  }

  // 스팀 스토어에서 가격을 새로 받아와 캐시 엔티티를 만들거나 갱신한다.
  // 조회에 실패하거나(비공개/삭제된 앱 등) 가격 정보가 없으면 기존 캐시를 건드리지 않고 null을 반환한다.
  private LibraryGamePrice fetchAndUpdatePrice(Long appId, LibraryGamePrice existing, LocalDateTime now) {
    SteamAppDetailsResponse detail = fetchAppDetails(appId);
    if (detail == null || !Boolean.TRUE.equals(detail.success()) || detail.data() == null) {
      return null;
    }

    boolean isFree = Boolean.TRUE.equals(detail.data().isFree());
    SteamAppDetailsResponse.PriceOverview overview = detail.data().priceOverview();
    Integer originalPriceKrw = isFree || overview == null ? 0 : overview.initial() / 100;
    Integer currentPriceKrw = isFree || overview == null ? 0 : overview.finalPrice() / 100;
    Integer discountPercent = overview == null ? 0 : overview.discountPercent();

    if (existing != null) {
      existing.update(isFree, originalPriceKrw, currentPriceKrw, discountPercent, now);
      return existing;
    }
    return LibraryGamePrice.builder()
        .appId(appId)
        .isFree(isFree)
        .originalPriceKrw(originalPriceKrw)
        .currentPriceKrw(currentPriceKrw)
        .discountPercent(discountPercent)
        .checkedAt(now)
        .build();
  }

  private SteamAppDetailsResponse fetchAppDetails(Long appId) {
    URI uri = UriComponentsBuilder.fromUriString(STEAM_STORE_BASE_URL + "/api/appdetails")
        .queryParam("appids", appId)
        .queryParam("cc", "kr")
        .queryParam("filters", "price_overview")
        .encode(StandardCharsets.UTF_8)
        .build()
        .toUri();

    try {
      Map<String, SteamAppDetailsResponse> response = restClient.get()
          .uri(uri)
          .retrieve()
          .body(new ParameterizedTypeReference<Map<String, SteamAppDetailsResponse>>() {
          });
      return response == null ? null : response.get(String.valueOf(appId));
    } catch (RestClientException e) {
      // 게임 한 건 조회 실패는 전체 요청을 막지 않고 건너뛴다 (기존 캐시 유지)
      return null;
    }
  }

  private LibraryGamePriceResponse toPriceResponse(LibraryGame game, LibraryGamePrice price) {
    if (price == null) {
      return new LibraryGamePriceResponse(game.getAppId(), game.getName(), null, null, null, null);
    }
    return new LibraryGamePriceResponse(
        game.getAppId(),
        game.getName(),
        price.getIsFree(),
        price.getOriginalPriceKrw(),
        price.getCurrentPriceKrw(),
        price.getDiscountPercent());
  }
}
