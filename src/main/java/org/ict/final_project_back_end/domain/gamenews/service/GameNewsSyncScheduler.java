package org.ict.final_project_back_end.domain.gamenews.service;

import org.ict.final_project_back_end.domain.gamenews.dto.GameNewsSyncResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.concurrent.atomic.AtomicLong;

@Component
@ConditionalOnProperty(prefix = "app.news.sync", name = "enabled", havingValue = "true")
public class GameNewsSyncScheduler {
  private static final Logger log = LoggerFactory.getLogger(GameNewsSyncScheduler.class);
  private final GameNewsSyncService sync;
  private final AtomicLong completedRuns = new AtomicLong();
  private volatile GameNewsSyncResult lastResult;
  public GameNewsSyncScheduler(GameNewsSyncService sync) { this.sync = sync; }

  @Scheduled(fixedDelayString = "${app.news.sync.poll-interval:PT5M}",
      initialDelayString = "${app.news.sync.initial-delay:PT30S}", scheduler = "gameNewsTaskScheduler")
  public void tick() {
    try { lastResult = sync.runOnce(); completedRuns.incrementAndGet(); }
    catch (RuntimeException failure) { log.error("News scheduled cycle failed type={}", failure.getClass().getSimpleName()); }
  }

  public long completedRuns() { return completedRuns.get(); }
  public GameNewsSyncResult lastResult() { return lastResult; }
}
