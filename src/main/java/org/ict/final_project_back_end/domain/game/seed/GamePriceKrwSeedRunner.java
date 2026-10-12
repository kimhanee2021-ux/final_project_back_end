package org.ict.final_project_back_end.domain.game.seed;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/*
 * games 테이블의 게임들에 대해 스팀 한국 가격을 받아 game_prices_krw에 저장하는 임시 러너.
 * - app.seed.prices.enabled=true 일 때만 동작 (기본 false)
 * - 12만 건 기준 약 1시간이 걸리므로 백그라운드(@Async)로 돌리고 서버 기동은 막지 않는다
 * - 이미 받은 게임은 건너뛰므로 중간에 서버를 꺼도 다음 기동 때 이어서 진행된다
 * - games CSV 시드가 끝난 뒤에 켜야 한다 (games가 비어 있으면 받을 대상이 없음)
 */
@Component
@RequiredArgsConstructor
public class GamePriceKrwSeedRunner implements ApplicationRunner {

  private final GamePriceKrwSyncService priceSyncService;

  @Value("${app.seed.prices.enabled:false}")
  private boolean seedEnabled;

  @Override
  public void run(ApplicationArguments args) {
    if (seedEnabled) {
      priceSyncService.fetchMissingPrices();
    }
  }
}
