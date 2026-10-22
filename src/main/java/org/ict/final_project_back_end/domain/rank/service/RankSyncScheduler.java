package org.ict.final_project_back_end.domain.rank.service;

import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 저장과 정기 실행을 모두 명시적으로 허용한 경우에만 등록한다. */
@Component
@ConditionalOnProperty(prefix = "app.rank", name = {"sync-enabled", "schedule-enabled"}, havingValue = "true")
public class RankSyncScheduler {
  private static final Logger log = LoggerFactory.getLogger(RankSyncScheduler.class);
  private final RankSyncService syncService;
  private final AtomicBoolean running = new AtomicBoolean();

  public RankSyncScheduler(RankSyncService syncService) {
    this.syncService = syncService;
  }

  // 매일 최신 확정 주차를 확인하므로 발표 지연이나 일시적인 실패는 다음 실행에서 재시도한다.
  // 서버 시작 시 즉시 실행하지 않으며, 예약 시각에만 실행한다.
  @Scheduled(cron = "${app.rank.sync-cron:0 0 6 * * *}", zone = "Asia/Seoul")
  public void collectLatest() {
    // 같은 서버 안에서 실행이 겹치는 경우에는 기존 작업을 유지한다.
    if (!running.compareAndSet(false, true)) {
      log.info("주간 순위 수집이 진행 중이므로 중복 실행을 건너뜁니다.");
      return;
    }
    try {
      // 두 국가의 검증이 끝난 뒤에만 저장하며, 저장 오류는 기존 트랜잭션에서 모두 롤백한다.
      var snapshots = syncService.syncWeeklyRanks(null);
      log.info("주간 순위 수집 완료: 주차={}, 국가 수={}", snapshots.getFirst().week(), snapshots.size());
    } catch (RuntimeException e) {
      // 실패 상태로 기존 차트를 덮어쓰지 않고 다음 예약 실행에서 다시 시도한다.
      log.error("주간 순위 수집 실패: 기존 데이터를 유지하고 다음 예약 시각에 재시도합니다.", e);
    } finally {
      running.set(false);
    }
  }
}
