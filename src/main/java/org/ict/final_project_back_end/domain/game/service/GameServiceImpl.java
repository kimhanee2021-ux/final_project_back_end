package org.ict.final_project_back_end.domain.game.service;

import org.ict.final_project_back_end.domain.game.dto.GameCursorPageResponse;
import org.ict.final_project_back_end.domain.game.dto.GameDetailResponse;
import org.ict.final_project_back_end.domain.game.dto.GamePageResponse;
import org.ict.final_project_back_end.domain.game.dto.GameResponse;
import org.ict.final_project_back_end.domain.game.dto.GameSyncStatusResponse;
import org.ict.final_project_back_end.domain.game.dto.SteamAppDetailsResponse;
import org.ict.final_project_back_end.domain.game.entity.Game;
import org.ict.final_project_back_end.domain.game.entity.GameDetail;
import org.ict.final_project_back_end.domain.game.entity.GameSyncState;
import org.ict.final_project_back_end.domain.game.repository.GameDetailRepository;
import org.ict.final_project_back_end.domain.game.repository.GameRepository;
import org.ict.final_project_back_end.domain.game.repository.GameSyncStateRepository;
import org.ict.final_project_back_end.global.exception.GameNotFoundException;
import org.ict.final_project_back_end.global.exception.InvalidRequestException;
import org.ict.final_project_back_end.global.exception.SteamApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.ict.final_project_back_end.global.constant.SteamUrls.STEAM_STORE_BASE_URL;

@Service
public class GameServiceImpl implements GameService {

  private static final Logger log = LoggerFactory.getLogger(GameServiceImpl.class);
  private static final Duration DETAIL_CACHE_TTL = Duration.ofDays(1);
  private static final int MAX_PAGE_SIZE = 100;
  // 전체 데이터를 나눠 받는 커서 조회는 응답을 더 크게 허용한다
  private static final int MAX_CURSOR_SIZE = 500;

  private final GameRepository gameRepository;
  private final GameDetailRepository gameDetailRepository;
  private final GameSyncStateRepository syncStateRepository;
  private final RestClient restClient = RestClient.create();

  public GameServiceImpl(GameRepository gameRepository,
                         GameDetailRepository gameDetailRepository,
                         GameSyncStateRepository syncStateRepository) {
    this.gameRepository = gameRepository;
    this.gameDetailRepository = gameDetailRepository;
    this.syncStateRepository = syncStateRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public GameCursorPageResponse getGamesAfter(Long afterAppId, int size) {
    int pageSize = Math.min(Math.max(size, 1), MAX_CURSOR_SIZE);
    PageRequest firstPage = PageRequest.of(0, pageSize);
    List<Game> games = afterAppId == null
        ? gameRepository.findByOrderByAppIdAsc(firstPage)
        : gameRepository.findByAppIdGreaterThanOrderByAppIdAsc(afterAppId, firstPage);
    List<GameResponse> content = games.stream().map(GameResponse::from).toList();
    // 한 페이지를 꽉 채웠다면 뒤에 더 있을 수 있으므로 다음 커서를 준다
    boolean hasNext = games.size() == pageSize;
    Long next = games.isEmpty() ? null : games.get(games.size() - 1).getAppId();
    return new GameCursorPageResponse(content, next, hasNext);
  }

  @Override
  @Transactional(readOnly = true)
  public GameSyncStatusResponse getSyncStatus() {
    GameSyncState state = syncStateRepository.findById(GameSyncState.SINGLETON_ID).orElse(null);
    return new GameSyncStatusResponse(
        gameRepository.count(),
        state == null ? null : state.getLastAppId(),
        state != null && state.isCompleted(),
        state == null ? null : state.getLastCompletedAt());
  }

  @Override
  @Transactional(readOnly = true)
  public GamePageResponse<GameResponse> getGames(int page, int size) {
    Page<Game> result = gameRepository.findAll(pageRequest(page, size, Sort.by("appId").ascending()));
    return GamePageResponse.from(result, GameResponse::from);
  }

  @Override
  @Transactional(readOnly = true)
  public GamePageResponse<GameResponse> searchGames(String keyword, int page, int size) {
    if (keyword == null || keyword.isBlank()) {
      throw new InvalidRequestException("검색어를 입력해 주세요");
    }
    Page<Game> result = gameRepository.findByNameContainingIgnoreCase(
        keyword.trim(), pageRequest(page, size, Sort.by("name").ascending()));
    return GamePageResponse.from(result, GameResponse::from);
  }

  // 외부 호출(스토어 API)이 들어가므로 트랜잭션을 걸지 않는다. 저장은 각 repository 호출 단위로 처리된다.
  @Override
  public GameDetailResponse getGameDetail(Long appId) {
    Game game = gameRepository.findByAppId(appId).orElseThrow(GameNotFoundException::new);
    GameDetail cached = gameDetailRepository.findByAppId(appId).orElse(null);
    if (cached != null && cached.getFetchedAt().isAfter(LocalDateTime.now().minus(DETAIL_CACHE_TTL))) {
      return GameDetailResponse.from(game, cached);
    }

    GameDetail detail;
    try {
      detail = saveDetail(appId, cached, fetchAppDetails(appId));
    } catch (SteamApiException e) {
      // 스팀 장애 때문에 상세 조회 전체가 실패하지 않도록, 오래된 캐시라도 있으면 그걸 돌려준다
      if (cached == null) {
        throw e;
      }
      log.warn("스팀 상세정보 갱신 실패, 캐시된 데이터를 반환합니다 appId={}", appId);
      detail = cached;
    }
    return GameDetailResponse.from(game, detail);
  }

  // 시드 전용: games 행 없이 상세정보만 확인한다. 출시 예정이면 저장하지 않고 false를 돌려준다.
  @Override
  public boolean cacheIfReleased(Long appId) {
    SteamAppDetailsResponse.Data data = fetchAppDetails(appId);
    if (data.releaseDate() != null && Boolean.TRUE.equals(data.releaseDate().comingSoon())) {
      return false;
    }
    GameDetail cached = gameDetailRepository.findByAppId(appId).orElse(null);
    saveDetail(appId, cached, data);
    return true;
  }

  private GameDetail saveDetail(Long appId, GameDetail cached, SteamAppDetailsResponse.Data data) {
    GameDetail detail = cached != null ? cached : GameDetail.builder().appId(appId).build();
    detail.update(
        data.type(),
        data.isFree(),
        data.shortDescription(),
        data.headerImage(),
        joinLines(data.developers()),
        joinLines(data.publishers()),
        joinLines(genreNames(data.genres())),
        data.releaseDate() == null ? null : data.releaseDate().date());
    SteamAppDetailsResponse.PriceOverview price = data.priceOverview();
    detail.updatePrice(
        price == null ? null : price.currency(),
        price == null ? null : price.initialPrice(),
        price == null ? null : price.finalPrice(),
        price == null ? null : price.discountPercent());
    return gameDetailRepository.save(detail);
  }

  private SteamAppDetailsResponse.Data fetchAppDetails(Long appId) {
    URI uri = UriComponentsBuilder.fromUriString(STEAM_STORE_BASE_URL + "/api/appdetails")
        .queryParam("appids", appId)
        .queryParam("l", "korean")
        .encode(StandardCharsets.UTF_8)
        .build()
        .toUri();

    Map<String, SteamAppDetailsResponse> response;
    try {
      response = restClient.get()
          .uri(uri)
          .retrieve()
          .body(new ParameterizedTypeReference<>() {
          });
    } catch (RestClientException e) {
      throw new SteamApiException();
    }

    SteamAppDetailsResponse entry = response == null ? null : response.get(String.valueOf(appId));
    if (entry == null || !Boolean.TRUE.equals(entry.success()) || entry.data() == null) {
      // 스팀 스토어에 상세정보가 없는 앱(비공개, 삭제 등)
      throw new GameNotFoundException();
    }
    return entry.data();
  }

  private String joinLines(List<String> values) {
    return values == null ? null : String.join("\n", values);
  }

  private List<String> genreNames(List<SteamAppDetailsResponse.Genre> genres) {
    return genres == null ? null : genres.stream().map(SteamAppDetailsResponse.Genre::description).toList();
  }

  // 페이지 크기는 과도한 조회를 막기 위해 상한을 둔다
  private Pageable pageRequest(int page, int size, Sort sort) {
    return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), sort);
  }
}
