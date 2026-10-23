package org.ict.final_project_back_end.domain.gamenews.service;

import org.ict.final_project_back_end.domain.gamenews.config.GameNewsSyncProperties;
import org.ict.final_project_back_end.domain.gamenews.dto.*;
import org.ict.final_project_back_end.domain.gamenews.dto.GameNewsSyncResult.*;
import org.ict.final_project_back_end.domain.gamenews.repository.GameNewsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Transactional(propagation = Propagation.NEVER)
public class GameNewsSyncService {
  private static final Logger log = LoggerFactory.getLogger(GameNewsSyncService.class);
  private final GameNewsSyncProperties options;
  private final GameNewsCollectionService collector;
  private final GameNewsBriefingService briefing;
  private final GameNewsRepository news;
  private final Clock clock;
  private final AtomicBoolean running = new AtomicBoolean();

  @Autowired
  public GameNewsSyncService(GameNewsSyncProperties options, GameNewsCollectionService collector,
      GameNewsBriefingService briefing, GameNewsRepository news) {
    this(options, collector, briefing, news, Clock.systemUTC());
  }

  GameNewsSyncService(GameNewsSyncProperties options, GameNewsCollectionService collector,
      GameNewsBriefingService briefing, GameNewsRepository news, Clock clock) {
    this.options = options; this.collector = collector; this.briefing = briefing; this.news = news; this.clock = clock;
  }

  public GameNewsSyncResult runOnce() {
    if (!running.compareAndSet(false, true)) return new GameNewsSyncResult(true, false, List.of());
    List<GameResult> results = new ArrayList<>();
    try {
      for (long appId : new LinkedHashSet<>(options.appIds())) {
        if (Thread.currentThread().isInterrupted()) break;
        GameNewsCollectionResult collected = null;
        CollectionStatus collectionStatus;
        try {
          collected = collector.collectIfDue(appId, options.newsCount()).orElse(null);
          collectionStatus = collected == null ? CollectionStatus.NOT_DUE : CollectionStatus.COLLECTED;
        } catch (RuntimeException failure) {
          collectionStatus = CollectionStatus.FAILED;
          log.warn("News collection failed app_id={} type={}", appId, failure.getClass().getSimpleName());
        }
        var outcomes = new EnumMap<NewsBriefingResult.Outcome, Integer>(NewsBriefingResult.Outcome.class);
        int errors = 0;
        // Briefing retries are independent of Steam availability and the 6-hour collection interval.
        if (!Thread.currentThread().isInterrupted()) {
          try {
            var now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
            var candidates = news.findBriefingCandidates(appId, now,
                now.minusMinutes(GameNewsBriefingWriter.LEASE_MINUTES), PageRequest.of(0, options.briefingBatchSize()));
            for (long newsId : candidates) {
              if (Thread.currentThread().isInterrupted()) break;
              try {
                var result = briefing.generateIfSourceChanged(newsId);
                outcomes.merge(result.outcome(), 1, Integer::sum);
              } catch (RuntimeException failure) {
                errors++;
                log.warn("News briefing failed app_id={} news_id={} type={}", appId, newsId, failure.getClass().getSimpleName());
              }
              if (!pause()) break;
            }
          } catch (RuntimeException failure) {
            errors++;
            log.warn("News candidate query failed app_id={} type={}", appId, failure.getClass().getSimpleName());
          }
        }
        var result = new GameResult(appId, collectionStatus, collected, Map.copyOf(outcomes), errors);
        results.add(result);
        log.info("News sync app_id={} collection={} inserted={} updated={} unchanged={} briefings={} errors={}",
            appId, collectionStatus, collected == null ? 0 : collected.inserted(), collected == null ? 0 : collected.updated(),
            collected == null ? 0 : collected.unchanged(), outcomes, errors);
        if (!pause()) break;
      }
      return new GameNewsSyncResult(false, Thread.currentThread().isInterrupted(), List.copyOf(results));
    } finally { running.set(false); }
  }

  private boolean pause() {
    try { Thread.sleep(options.requestDelayMs()); return true; }
    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return false; }
  }
}
