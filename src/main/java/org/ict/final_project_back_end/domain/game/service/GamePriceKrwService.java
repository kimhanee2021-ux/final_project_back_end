package org.ict.final_project_back_end.domain.game.service;

import lombok.extern.slf4j.Slf4j;
import org.ict.final_project_back_end.domain.game.entity.GamePriceKrw;
import org.ict.final_project_back_end.domain.game.repository.GamePriceKrwRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// 원화 가격 캐시(game_prices_krw)를 채우는 단일 진입점.
// 가격이 필요한 곳(소비 분석, 보유 게임 가격 조회 등)은 ensurePrices()만 부르면 된다.
//   - 저장된 가격이 없거나 하루가 지난 게임만 골라서
//   - 100개씩 묶어 스팀에 묻고 (트랜잭션 밖에서 호출, 저장만 짧게)
//   - 가격 없음/미판매도 상태로 저장해서 같은 게임을 반복해서 묻지 않는다.
@Slf4j
@Service
public class GamePriceKrwService {

  // 할인가가 바뀌므로 하루 지난 가격은 다시 받는다
  static final Duration PRICE_TTL = Duration.ofDays(1);
  // 한 번의 API 요청에서 동기로 처리할 최대 묶음 수. 넘는 게임은 다음 요청 때 이어서 채운다(프론트 타임아웃 방지).
  static final int MAX_BATCHES_PER_CALL = 10;
  // 무료 여부 확인은 게임당 요청 1번이라 한 번에 몇 개만 한다
  static final int MAX_FREE_CHECKS_PER_CALL = 20;
  private static final long BATCH_INTERVAL_MS = 300;

  private final GamePriceKrwRepository priceRepository;
  private final SteamPriceClient steamPriceClient;

  public GamePriceKrwService(GamePriceKrwRepository priceRepository, SteamPriceClient steamPriceClient) {
    this.priceRepository = priceRepository;
    this.steamPriceClient = steamPriceClient;
  }

  // 주어진 게임들의 원화 가격을 최신 상태로 만든다. 스팀 호출이 실패하면 기존 캐시를 그대로 둔다(예외를 던지지 않음).
  public void ensurePrices(Collection<Long> appIds) {
    if (appIds.isEmpty()) {
      return;
    }
    LocalDateTime now = LocalDateTime.now();
    Map<Long, GamePriceKrw> cached = priceRepository.findAllById(appIds).stream()
        .collect(Collectors.toMap(GamePriceKrw::getAppId, Function.identity()));
    List<Long> targets = selectTargets(appIds, cached, now);
    if (targets.isEmpty()) {
      return;
    }

    List<GamePriceKrw> fetched = new ArrayList<>();
    int batches = 0;
    for (int i = 0; i < targets.size() && batches < MAX_BATCHES_PER_CALL; i += SteamPriceClient.MAX_BATCH_SIZE, batches++) {
      List<Long> chunk = targets.subList(i, Math.min(i + SteamPriceClient.MAX_BATCH_SIZE, targets.size()));
      try {
        if (batches > 0) {
          Thread.sleep(BATCH_INTERVAL_MS);
        }
        fetched.addAll(toEntities(chunk, steamPriceClient.fetchPrices(chunk), now));
      } catch (RestClientException e) {
        log.warn("원화 가격 조회 실패, 기존 캐시 유지 ({}건)", chunk.size(), e);
        break;
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }
    markFreeGames(fetched, now);
    priceRepository.saveAll(fetched);
  }

  // 가격 정보가 없다고 나온 게임 중 일부를 무료 여부까지 확인해 FREE로 바꾼다
  private void markFreeGames(List<GamePriceKrw> fetched, LocalDateTime now) {
    int checks = 0;
    for (int i = 0; i < fetched.size() && checks < MAX_FREE_CHECKS_PER_CALL; i++) {
      GamePriceKrw price = fetched.get(i);
      if (!GamePriceKrw.NO_PRICE.equals(price.getStatus())) {
        continue;
      }
      checks++;
      try {
        if (Boolean.TRUE.equals(steamPriceClient.fetchIsFree(price.getAppId()))) {
          fetched.set(i, GamePriceKrw.builder().appId(price.getAppId()).status(GamePriceKrw.FREE)
              .initialKrw(0).finalKrw(0).discountPercent(0).fetchedAt(now).build());
        }
      } catch (RestClientException e) {
        log.warn("무료 여부 확인 실패 appId={}", price.getAppId());
        return;
      }
    }
  }

  // 아직 가격이 없거나, 마지막으로 받은 지 하루가 지난 게임만 고른다
  static List<Long> selectTargets(Collection<Long> appIds, Map<Long, GamePriceKrw> cached, LocalDateTime now) {
    LocalDateTime staleBefore = now.minus(PRICE_TTL);
    return appIds.stream()
        .distinct()
        .filter(appId -> {
          GamePriceKrw price = cached.get(appId);
          return price == null || price.getFetchedAt().isBefore(staleBefore);
        })
        .toList();
  }

  // 스팀 응답을 캐시 엔티티로 바꾼다. 응답에 없거나 success=false인 게임도 UNAVAILABLE로 저장해 반복 요청을 막는다.
  public static List<GamePriceKrw> toEntities(List<Long> appIds, Map<String, SteamPriceClient.AppPrice> prices,
                                       LocalDateTime now) {
    List<GamePriceKrw> result = new ArrayList<>(appIds.size());
    for (Long appId : appIds) {
      SteamPriceClient.AppPrice app = prices.get(String.valueOf(appId));
      GamePriceKrw.GamePriceKrwBuilder builder = GamePriceKrw.builder().appId(appId).fetchedAt(now);
      SteamPriceClient.PriceOverview p = app == null ? null : app.priceOverview();
      if (app == null || !app.success()) {
        builder.status(GamePriceKrw.UNAVAILABLE);
      } else if (p != null && "KRW".equals(p.currency())) {
        builder.status(GamePriceKrw.PRICED)
            .initialKrw(p.initial() / 100)
            .finalKrw(p.finalPrice() / 100)
            .discountPercent(p.discountPercent());
      } else {
        builder.status(GamePriceKrw.NO_PRICE);
      }
      result.add(builder.build());
    }
    return result;
  }
}
