package org.ict.final_project_back_end.domain.game.scheduler;

import org.ict.final_project_back_end.domain.game.service.GameSyncService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

// 개발용 초기 데이터 시드. game.seed.limit(또는 환경변수 GAME_SEED_LIMIT)이 0보다 클 때만 기동 시 한 번 실행한다.
// 데이터를 채운 뒤에는 설정을 0(기본값)으로 되돌리거나 이 클래스를 삭제한다.
@Component
public class GameSeedRunner implements ApplicationRunner {

  private final GameSyncService gameSyncService;
  private final int seedLimit;

  public GameSeedRunner(GameSyncService gameSyncService,
                        @Value("${game.seed.limit:0}") int seedLimit) {
    this.gameSyncService = gameSyncService;
    this.seedLimit = seedLimit;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (seedLimit > 0) {
      gameSyncService.seedFirst(seedLimit);
    }
  }
}
