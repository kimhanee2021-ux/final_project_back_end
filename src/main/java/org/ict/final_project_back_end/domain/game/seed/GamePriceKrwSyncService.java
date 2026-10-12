package org.ict.final_project_back_end.domain.game.seed;

import lombok.extern.slf4j.Slf4j;
import org.ict.final_project_back_end.domain.game.repository.GamePriceKrwRepository;
import org.ict.final_project_back_end.domain.game.service.GamePriceKrwService;
import org.ict.final_project_back_end.domain.game.service.SteamPriceClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

// 개발용: games 테이블 전체(약 12만 건)의 원화 가격을 미리 받아 둔다.
// 평소에는 필요 없다 - 보유 게임 가격은 GamePriceKrwService.ensurePrices()가 소비 분석 조회 때 알아서 채운다.
@Slf4j
@Service
public class GamePriceKrwSyncService {

  // 스팀 상점 호출 제한(약 5분에 200회)에 걸리지 않도록 요청 사이에 쉰다
  private static final long REQUEST_INTERVAL_MS = 2000;
  private static final long RETRY_WAIT_MS = 60_000;
  private static final int MAX_RETRY = 5;

  private final GamePriceKrwRepository priceRepository;
  private final SteamPriceClient steamPriceClient;

  public GamePriceKrwSyncService(GamePriceKrwRepository priceRepository, SteamPriceClient steamPriceClient) {
    this.priceRepository = priceRepository;
    this.steamPriceClient = steamPriceClient;
  }

  @Async
  public void fetchMissingPrices() {
    List<Long> appIds = priceRepository.findAppIdsWithoutPrice();
    int chunkSize = SteamPriceClient.MAX_BATCH_SIZE;
    log.info("원화 가격 시드 시작: 대상 {}건", appIds.size());
    try {
      for (int i = 0; i < appIds.size(); i += chunkSize) {
        List<Long> chunk = appIds.subList(i, Math.min(i + chunkSize, appIds.size()));
        priceRepository.saveAll(GamePriceKrwService.toEntities(chunk, fetchWithRetry(chunk), LocalDateTime.now()));
        if ((i / chunkSize) % 50 == 0) {
          log.info("원화 가격 시드 진행: {}/{}", Math.min(i + chunkSize, appIds.size()), appIds.size());
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

  // 시드는 오래 걸려도 되므로, 실패하면 1분씩 늘려 가며 최대 5번 다시 시도한다
  private Map<String, SteamPriceClient.AppPrice> fetchWithRetry(List<Long> appIds) throws InterruptedException {
    for (int attempt = 1; ; attempt++) {
      try {
        return steamPriceClient.fetchPrices(appIds);
      } catch (RestClientException e) {
        if (attempt >= MAX_RETRY) {
          throw e;
        }
        log.warn("스팀 가격 요청 실패, {}초 후 재시도 ({}/{})", RETRY_WAIT_MS * attempt / 1000, attempt, MAX_RETRY);
        Thread.sleep(RETRY_WAIT_MS * attempt);
      }
    }
  }
}
