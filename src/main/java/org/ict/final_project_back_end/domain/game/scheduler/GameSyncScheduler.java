package org.ict.final_project_back_end.domain.game.scheduler;

import org.ict.final_project_back_end.domain.game.service.GameSyncService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 매일 새벽 4시에 게임 동기화 실행 여부를 확인한다. 실제 판단(1일 1회, 이어하기)은 GameSyncService가 한다.
@Component
public class GameSyncScheduler {

  private final GameSyncService gameSyncService;

  public GameSyncScheduler(GameSyncService gameSyncService) {
    this.gameSyncService = gameSyncService;
  }

  @Scheduled(cron = "0 0 4 * * *")
  public void runDailySync() {
    gameSyncService.syncIfDue();
  }
}
