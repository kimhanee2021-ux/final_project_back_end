package org.ict.final_project_back_end.domain.game.service;

import lombok.extern.slf4j.Slf4j;
import org.ict.final_project_back_end.domain.game.repository.GameRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.util.List;

// games 테이블의 할인가 표시(price_info)와 할인율(discount_percent)을 스팀 IStoreBrowseService/GetItems로 매일 한 번 갱신한다.
// 개발용 기능이라 app.price-sync.enabled=true 일 때만 빈이 만들어진다(기본 false, 꺼져 있으면 스케줄도 돌지 않는다).
// 정가(original_price)는 CSV 시드 값을 그대로 두고 건드리지 않는다.
// appId 오름차순으로 전체 게임을 100개씩 묶어 물어보고, 응답에 가격이 없는 게임(출시 전, 판매 중지 등)은 기존 값을 유지한다.
// 스팀 호출이 실패하면 거기서 멈추고(예외를 던지지 않음) 이미 갱신한 것은 그대로 둔 채 다음 스케줄에 처음부터 다시 시도한다.
@Slf4j
@Service
@ConditionalOnProperty(name = "app.price-sync.enabled", havingValue = "true")
public class GamePriceSyncService {

  private static final String UPDATE_SQL =
      "UPDATE games SET price_info = ?, discount_percent = ?, last_synced_at = NOW() WHERE app_id = ?";

  private final SteamStoreBrowseClient client;
  private final GameRepository gameRepository;
  private final JdbcTemplate jdbcTemplate;

  @Value("${app.price-sync.request-interval-ms:500}")
  private long requestIntervalMs;

  public GamePriceSyncService(SteamStoreBrowseClient client, GameRepository gameRepository,
      JdbcTemplate jdbcTemplate) {
    this.client = client;
    this.gameRepository = gameRepository;
    this.jdbcTemplate = jdbcTemplate;
  }

  // 매일 새벽 3:30(KST). featured 캐시 갱신(4:10)과 겹치지 않게 앞당겨 두었고, app.price-sync.cron으로 바꿀 수 있다.
  @Scheduled(cron = "${app.price-sync.cron:0 30 3 * * *}", zone = "Asia/Seoul")
  public void syncAll() {
    long started = System.currentTimeMillis();
    long lastAppId = 0;
    long requested = 0;
    long updated = 0;

    while (true) {
      List<Long> appIds = gameRepository.findAppIdsAfter(lastAppId, PageRequest.of(0, SteamStoreBrowseClient.MAX_BATCH_SIZE));
      if (appIds.isEmpty()) {
        break;
      }
      lastAppId = appIds.get(appIds.size() - 1);
      requested += appIds.size();

      List<SteamStoreBrowseClient.PriceItem> items;
      try {
        items = client.fetchPrices(appIds);
      } catch (RestClientException e) {
        log.warn("스팀 GetItems 조회 실패, 가격 동기화를 중단합니다 (마지막 appId={})", lastAppId, e);
        break;
      }
      if (!items.isEmpty()) {
        jdbcTemplate.batchUpdate(UPDATE_SQL, items, items.size(), (ps, item) -> {
          ps.setString(1, item.priceInfo());
          ps.setInt(2, item.discountPercent());
          ps.setLong(3, item.appId());
        });
        updated += items.size();
      }

      try {
        Thread.sleep(requestIntervalMs);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }
    log.info("games 가격 동기화 종료: 조회 {}건, 갱신 {}건, {}ms", requested, updated, System.currentTimeMillis() - started);
  }
}
