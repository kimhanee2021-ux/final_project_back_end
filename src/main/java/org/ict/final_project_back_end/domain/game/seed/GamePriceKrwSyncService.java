package org.ict.final_project_back_end.domain.game.seed;

import lombok.extern.slf4j.Slf4j;
import org.ict.final_project_back_end.domain.game.entity.GamePriceKrw;
import org.ict.final_project_back_end.domain.game.repository.GamePriceKrwRepository;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.ict.final_project_back_end.global.constant.SteamUrls.STEAM_STORE_BASE_URL;

// 스팀 상점 appdetails API로 한국 가격을 받아온다.
// filters=price_overview를 붙이면 appId 여러 개를 한 번에 물을 수 있어 100개씩 묶어서 요청한다.
@Slf4j
@Service
public class GamePriceKrwSyncService {

  private static final int CHUNK_SIZE = 100;
  // 스팀 상점 호출 제한(약 5분에 200회)에 걸리지 않도록 요청 사이에 쉰다
  private static final long REQUEST_INTERVAL_MS = 2000;
  private static final long RETRY_WAIT_MS = 60_000;
  private static final int MAX_RETRY = 5;

  private final GamePriceKrwRepository priceRepository;
  private final RestClient restClient = RestClient.create();

  public GamePriceKrwSyncService(GamePriceKrwRepository priceRepository) {
    this.priceRepository = priceRepository;
  }

  @Async
  public void fetchMissingPrices() {
    List<Long> appIds = priceRepository.findAppIdsWithoutPrice();
    log.info("원화 가격 시드 시작: 대상 {}건", appIds.size());
    try {
      for (int i = 0; i < appIds.size(); i += CHUNK_SIZE) {
        List<Long> chunk = appIds.subList(i, Math.min(i + CHUNK_SIZE, appIds.size()));
        priceRepository.saveAll(toEntities(chunk, fetchWithRetry(chunk)));
        if ((i / CHUNK_SIZE) % 50 == 0) {
          log.info("원화 가격 시드 진행: {}/{}", Math.min(i + CHUNK_SIZE, appIds.size()), appIds.size());
        }
        Thread.sleep(REQUEST_INTERVAL_MS);
      }
      log.info("원화 가격 시드 완료: {}건", appIds.size());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("원화 가격 시드가 중단되었습니다 (다음 기동 시 이어서 진행)");
    } catch (RestClientException e) {
      log.warn("스팀 응답 실패가 계속되어 원화 가격 시드를 중단합니다 (다음 기동 시 이어서 진행)", e);
    }
  }

  private Map<String, AppPrice> fetchWithRetry(List<Long> appIds) throws InterruptedException {
    URI uri = UriComponentsBuilder.fromUriString(STEAM_STORE_BASE_URL + "/api/appdetails")
        .queryParam("cc", "kr")
        .queryParam("filters", "price_overview")
        .queryParam("appids", appIds.stream().map(String::valueOf).collect(Collectors.joining(",")))
        .build(true)
        .toUri();
    for (int attempt = 1; ; attempt++) {
      try {
        Map<String, AppPrice> body = restClient.get().uri(uri).retrieve()
            .body(new ParameterizedTypeReference<>() {});
        return body == null ? Map.of() : body;
      } catch (RestClientException e) {
        if (attempt >= MAX_RETRY) {
          throw e;
        }
        log.warn("스팀 가격 요청 실패, {}초 후 재시도 ({}/{})", RETRY_WAIT_MS * attempt / 1000, attempt, MAX_RETRY);
        Thread.sleep(RETRY_WAIT_MS * attempt);
      }
    }
  }

  private List<GamePriceKrw> toEntities(List<Long> appIds, Map<String, AppPrice> prices) {
    LocalDateTime now = LocalDateTime.now();
    List<GamePriceKrw> result = new ArrayList<>(appIds.size());
    for (Long appId : appIds) {
      AppPrice app = prices.get(String.valueOf(appId));
      GamePriceKrw.GamePriceKrwBuilder builder = GamePriceKrw.builder().appId(appId).fetchedAt(now);
      if (app == null || !app.success()) {
        builder.status(GamePriceKrw.UNAVAILABLE);
      } else if (app.priceOverview() != null && "KRW".equals(app.priceOverview().currency())) {
        // 스팀은 원화도 100을 곱한 값으로 내려준다 (1600000 = 16,000원)
        PriceOverview p = app.priceOverview();
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

  // 가격 정보가 없으면 data가 객체가 아니라 빈 배열([])로 오므로 Object로 받아 직접 꺼낸다
  record AppPrice(boolean success, Object data) {
    PriceOverview priceOverview() {
      if (!(data instanceof Map<?, ?> map) || !(map.get("price_overview") instanceof Map<?, ?> p)) {
        return null;
      }
      return new PriceOverview((String) p.get("currency"), toInt(p.get("initial")), toInt(p.get("final")),
          toInt(p.get("discount_percent")));
    }

    private static int toInt(Object value) {
      return value instanceof Number n ? n.intValue() : 0;
    }
  }

  record PriceOverview(String currency, int initial, int finalPrice, int discountPercent) {
  }
}
