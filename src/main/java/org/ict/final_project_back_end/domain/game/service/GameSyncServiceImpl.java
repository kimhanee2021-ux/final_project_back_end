package org.ict.final_project_back_end.domain.game.service;

import org.ict.final_project_back_end.domain.game.dto.SteamAppListResponse;
import org.ict.final_project_back_end.domain.game.entity.Game;
import org.ict.final_project_back_end.domain.game.entity.GameSyncState;
import org.ict.final_project_back_end.domain.game.repository.GameRepository;
import org.ict.final_project_back_end.domain.game.repository.GameSyncStateRepository;
import org.ict.final_project_back_end.global.exception.GameNotFoundException;
import org.ict.final_project_back_end.global.exception.SteamApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.ict.final_project_back_end.global.constant.SteamUrls.STEAM_APP_LIST_URL;

@Service
public class GameSyncServiceImpl implements GameSyncService {

  private static final Logger log = LoggerFactory.getLogger(GameSyncServiceImpl.class);
  // 한 번의 스팀 요청으로 가져올 앱 개수
  private static final int PAGE_SIZE = 200;
  // 시드용: 목록 요청 한 번에 받을 개수
  private static final int SEED_PAGE_SIZE = 10000;
  // 요청 한 번 끝난 뒤 다음 요청까지 기다리는 시간
  private static final Duration REQUEST_INTERVAL = Duration.ofMinutes(5);
  // 마지막 완료 후 이 기간이 지나야 새 한 바퀴를 시작한다
  private static final Duration REFRESH_INTERVAL = Duration.ofDays(1);
  // 시드용: 상세정보 요청 사이 대기 시간 (스토어 API 호출 제한 회피용)
  private static final Duration DETAIL_REQUEST_INTERVAL = Duration.ofMillis(1500);

  private final GameRepository gameRepository;
  private final GameSyncStateRepository syncStateRepository;
  private final GameService gameService;
  private final RestClient restClient = RestClient.create();
  private final String steamApiKey;
  // 같은 JVM에서 동기화가 두 번 겹치지 않도록 막는다
  private final AtomicBoolean running = new AtomicBoolean(false);

  public GameSyncServiceImpl(GameRepository gameRepository,
                             GameSyncStateRepository syncStateRepository,
                             GameService gameService,
                             @Value("${steam.api-key}") String steamApiKey) {
    this.gameRepository = gameRepository;
    this.syncStateRepository = syncStateRepository;
    this.gameService = gameService;
    this.steamApiKey = steamApiKey;
  }

  // 트랜잭션을 걸지 않는다. 한 바퀴가 며칠 걸리므로 그동안 DB 커넥션을 붙잡으면 안 되고, 배치마다 따로 저장한다.
  @Override
  @Async
  public void syncIfDue() {
    if (!running.compareAndSet(false, true)) {
      log.info("전체 게임 동기화가 이미 진행 중이라 이번 실행은 건너뜁니다");
      return;
    }
    try {
      crawlAll();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("전체 게임 동기화가 중단되었습니다. 저장된 커서부터 다음 실행 때 이어서 진행합니다");
    } finally {
      running.set(false);
    }
  }

  @Override
  @Async
  public void seedFirst(int limit) {
    if (!running.compareAndSet(false, true)) {
      log.info("동기화가 이미 진행 중이라 시드를 건너뜁니다");
      return;
    }
    try {
      seedReleased(fetchAllApps(), limit);
    } catch (SteamApiException e) {
      log.warn("시드 중 스팀 API 호출에 실패해 중단합니다");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("시드가 중단되었습니다");
    } finally {
      running.set(false);
    }
  }

  // 시드용 목록 조회. 요청 횟수를 줄이려고 한 번에 최대치(SEED_PAGE_SIZE)씩 받는다.
  private List<SteamAppListResponse.App> fetchAllApps() {
    List<SteamAppListResponse.App> all = new ArrayList<>();
    long cursor = 0;
    while (true) {
      SteamAppListResponse.Body body = fetchPage(cursor, SEED_PAGE_SIZE);
      List<SteamAppListResponse.App> apps = body.apps() == null ? List.of() : body.apps();
      if (apps.isEmpty()) {
        break;
      }
      all.addAll(apps);
      cursor = nextCursor(body, apps, cursor);
      if (!Boolean.TRUE.equals(body.haveMoreResults())) {
        break;
      }
    }
    return all;
  }

  // appId가 큰 순서(최신 게임 우선)로 보면서 출시된 게임이 limit개 모일 때까지 상세정보를 확인한다.
  // 출시 예정 게임은 상세정보 확인 단계에서 걸러져 DB에 저장되지 않는다.
  private void seedReleased(List<SteamAppListResponse.App> apps, int limit) throws InterruptedException {
    List<SteamAppListResponse.App> newestFirst = apps.stream()
        .sorted(Comparator.comparing(SteamAppListResponse.App::appid).reversed())
        .toList();
    int saved = 0;
    int comingSoon = 0;
    for (SteamAppListResponse.App app : newestFirst) {
      if (saved >= limit) {
        break;
      }
      try {
        if (gameService.cacheIfReleased(app.appid())) {
          saveBatch(List.of(app));
          saved++;
        } else {
          comingSoon++;
        }
      } catch (GameNotFoundException | SteamApiException e) {
        log.warn("시드 건너뜀 appId={} 사유={}", app.appid(), e.getClass().getSimpleName());
      }
      Thread.sleep(DETAIL_REQUEST_INTERVAL.toMillis());
    }
    log.info("시드 완료: 출시된 게임 {}개 저장 (출시 예정 제외 {}개)", saved, comingSoon);
  }

  private void crawlAll() throws InterruptedException {
    GameSyncState state = syncStateRepository.findById(GameSyncState.SINGLETON_ID)
        .orElseGet(() -> syncStateRepository.save(GameSyncState.create()));

    if (state.isCompleted()) {
      if (state.getLastCompletedAt().isAfter(LocalDateTime.now().minus(REFRESH_INTERVAL))) {
        log.info("최근 24시간 안에 전체 동기화가 끝났으므로 건너뜁니다");
        return;
      }
      state.restart();
      log.info("새 한 바퀴의 전체 게임 동기화를 처음(appId 0)부터 시작합니다");
    } else {
      log.info("전체 게임 동기화를 appId {} 다음부터 이어서 시작합니다", state.getLastAppId());
    }

    long cursor = state.getLastAppId();
    while (true) {
      SteamAppListResponse.Body body = fetchPage(cursor, PAGE_SIZE);
      List<SteamAppListResponse.App> apps = body.apps() == null ? List.of() : body.apps();
      saveBatch(apps);

      cursor = nextCursor(body, apps, cursor);
      state.advance(cursor);
      syncStateRepository.save(state);
      log.info("전체 게임 동기화: {}개 저장, 다음 시작 appId={}", apps.size(), cursor);

      if (apps.isEmpty() || !Boolean.TRUE.equals(body.haveMoreResults())) {
        break;
      }
      Thread.sleep(REQUEST_INTERVAL.toMillis());
    }

    state.complete(LocalDateTime.now());
    syncStateRepository.save(state);
    log.info("전체 게임 동기화 한 바퀴 완료");
  }

  private SteamAppListResponse.Body fetchPage(long lastAppId, int pageSize) {
    URI uri = UriComponentsBuilder.fromUriString(STEAM_APP_LIST_URL)
        .queryParam("key", steamApiKey)
        .queryParam("max_results", pageSize)
        .queryParam("last_appid", lastAppId)
        .queryParam("include_games", true)
        .encode(StandardCharsets.UTF_8)
        .build()
        .toUri();

    SteamAppListResponse response;
    try {
      response = restClient.get()
          .uri(uri)
          .retrieve()
          .body(SteamAppListResponse.class);
    } catch (RestClientException e) {
      // 예외 메시지에는 요청 URL(=API 키 포함)이 섞일 수 있으므로 원본 예외는 전달하지 않는다
      throw new SteamApiException();
    }

    if (response == null || response.response() == null) {
      throw new SteamApiException();
    }
    return response.response();
  }

  // 스팀이 last_appid를 주면 그 값을, 없으면 이번 배치의 가장 큰 appId를 다음 커서로 쓴다 (항상 앞으로만 전진)
  private long nextCursor(SteamAppListResponse.Body body, List<SteamAppListResponse.App> apps, long cursor) {
    if (body.lastAppId() != null) {
      return body.lastAppId();
    }
    return apps.stream()
        .map(SteamAppListResponse.App::appid)
        .max(Long::compare)
        .orElse(cursor);
  }

  // appId 기준으로 기존 행이 있으면 이름만 갱신하고, 없으면 새로 만든다 (재실행해도 중복 저장되지 않게)
  private void saveBatch(List<SteamAppListResponse.App> apps) {
    if (apps.isEmpty()) {
      return;
    }
    Map<Long, Game> existingByAppId = gameRepository
        .findAllByAppIdIn(apps.stream().map(SteamAppListResponse.App::appid).toList())
        .stream()
        .collect(Collectors.toMap(Game::getAppId, Function.identity()));

    List<Game> toSave = apps.stream()
        .map(app -> {
          String name = app.name() == null ? "" : app.name();
          Game existing = existingByAppId.get(app.appid());
          if (existing == null) {
            return Game.builder().appId(app.appid()).name(name).build();
          }
          existing.rename(name);
          return existing;
        })
        .toList();
    gameRepository.saveAll(toSave);
  }
}
