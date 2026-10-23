package org.ict.final_project_back_end.domain.gamenews.service;

import org.ict.final_project_back_end.domain.game.entity.Game;
import org.ict.final_project_back_end.domain.game.repository.GameRepository;
import org.ict.final_project_back_end.domain.gamenews.client.*;
import org.ict.final_project_back_end.domain.gamenews.config.*;
import org.ict.final_project_back_end.domain.gamenews.dto.*;
import org.ict.final_project_back_end.domain.gamenews.entity.*;
import org.ict.final_project_back_end.domain.gamenews.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.LongStream;
import static org.ict.final_project_back_end.domain.gamenews.dto.GameNewsSyncResult.CollectionStatus.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(showSql = false, properties = {"spring.test.database.replace=NONE",
    "spring.datasource.url=jdbc:h2:mem:news-sync-tests;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "app.news.sync.app-ids=900301,900302", "app.news.sync.request-delay-ms=0"})
@Import({GameNewsWriter.class, GameNewsBriefingWriter.class, GameNewsCollectionService.class,
    GameNewsBriefingService.class, GameNewsSyncService.class, GameNewsSyncConfiguration.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GameNewsSyncServiceTest {
  static final long APP = 900301, OTHER = 900302, EXCLUDED = 900303;
  @Autowired GameNewsSyncService sync;
  @Autowired GameNewsCollectionService collector;
  @Autowired GameNewsBriefingService briefing;
  @Autowired GameNewsWriter writer;
  @Autowired GameRepository games;
  @Autowired GameNewsRepository news;
  @Autowired GameNewsBriefingRepository briefings;
  @Autowired GameNewsSyncStateRepository states;
  @Autowired JdbcTemplate db;
  @MockitoBean SteamNewsClient steam;
  @MockitoBean NewsBriefingAiClient ai;

  @BeforeEach void setup() {
    briefings.deleteAllInBatch(); news.deleteAllInBatch(); states.deleteAllInBatch(); games.deleteAllInBatch();
    for (long id : List.of(APP, OTHER, EXCLUDED)) games.saveAndFlush(Game.builder().appId(id).name("Game " + id).build());
    when(steam.fetch(APP, 5)).thenReturn(List.of(item(APP, "1", "original")));
    when(steam.fetch(OTHER, 5)).thenReturn(List.of(item(OTHER, "1", "original")));
    when(ai.modelName()).thenReturn("test-model"); when(ai.promptVersion()).thenReturn("news-ko-v1");
    when(ai.generate(any())).thenAnswer(call -> {
      assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
      return new NewsBriefingAiClient.Draft(GameNewsCategory.PATCH, "한국어 요약입니다.", "한국어 브리핑 본문입니다.");
    });
  }

  @Test void firstCycleCollectsAndGeneratesThenSkipsUnchangedAndOnlyTargetsConfiguredGames() {
    var first = sync.runOnce();
    assertEquals(2, first.games().size());
    assertTrue(first.games().stream().allMatch(g -> g.collectionStatus() == COLLECTED && g.collection().inserted() == 1));
    var generated = briefings.findAll().stream().map(GameNewsBriefing::getGeneratedAt).toList();
    var second = sync.runOnce();
    assertTrue(second.games().stream().allMatch(g -> g.collectionStatus() == NOT_DUE && g.briefings().isEmpty()));
    assertEquals(generated, briefings.findAll().stream().map(GameNewsBriefing::getGeneratedAt).toList());
    assertEquals(2, news.count()); assertEquals(2, briefings.count()); assertEquals(2, states.count());
    verify(steam, times(1)).fetch(APP, 5); verify(steam, times(1)).fetch(OTHER, 5);
    verify(steam, never()).fetch(eq(EXCLUDED), anyInt()); verify(ai, times(2)).generate(any());
  }

  @Test void nextDueCollectionGeneratesOnlyNewOrChangedSources() {
    sync.runOnce(); due(APP); due(OTHER);
    when(steam.fetch(APP, 5)).thenReturn(List.of(item(APP, "1", "changed"), item(APP, "2", "new")));
    var result = sync.runOnce();
    assertEquals(1, result.games().getFirst().collection().inserted());
    assertEquals(1, result.games().getFirst().collection().updated());
    assertEquals(1, result.games().getLast().collection().unchanged());
    assertTrue(result.games().getLast().briefings().isEmpty());
    verify(ai, times(4)).generate(any()); assertEquals(3, briefings.count());
    for (var b : briefings.findAll()) assertEquals(news.findById(b.getNewsId()).orElseThrow().getContentHash(), b.getGeneratedFromHash());
  }

  @Test void scheduledChecksIgnoreModelOnlyChangesEvenAfterCollectionAndLockRecheck() {
    sync.runOnce(); due(APP); due(OTHER);
    when(ai.modelName()).thenReturn("new-model"); when(ai.promptVersion()).thenReturn("news-ko-v2");
    assertTrue(sync.runOnce().games().stream().allMatch(g -> g.briefings().isEmpty()));
    for (var n : news.findAll()) assertEquals(NewsBriefingResult.Outcome.UP_TO_DATE, briefing.generateIfSourceChanged(n.getId()).outcome());
    verify(ai, times(2)).generate(any());
  }

  @Test void failedCollectionDoesNotBlockCachedBriefingsOrOtherGamesAndUsesBackoff() {
    sync.runOnce(); due(APP); due(OTHER);
    writer.save(APP, List.of(item(APP, "1", "changed")), LocalDateTime.now(ZoneOffset.UTC), LocalDateTime.now(ZoneOffset.UTC));
    due(APP);
    when(steam.fetch(APP, 5)).thenThrow(new IllegalStateException("Steam unavailable"));
    var result = sync.runOnce();
    assertEquals(FAILED, result.games().getFirst().collectionStatus());
    assertEquals(1, result.games().getFirst().briefings().get(NewsBriefingResult.Outcome.GENERATED));
    assertEquals(COLLECTED, result.games().getLast().collectionStatus());
    assertEquals(1, states.findById(APP).orElseThrow().getConsecutiveFailures());
    assertEquals(NOT_DUE, sync.runOnce().games().getFirst().collectionStatus());
    verify(steam, times(2)).fetch(APP, 5); verify(ai, times(3)).generate(any());
  }

  @Test void failedBriefingsWaitAndRetryWithoutWaitingForNextSteamCollection() {
    when(ai.generate(any())).thenThrow(new IllegalStateException("AI unavailable"));
    sync.runOnce(); sync.runOnce(); verify(ai, times(2)).generate(any());
    db.update("update game_news_briefings set next_retry_at = ?", LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));
    doReturn(new NewsBriefingAiClient.Draft(GameNewsCategory.PATCH, "복구 요약입니다.", "복구 본문입니다.")).when(ai).generate(any());
    var recovered = sync.runOnce();
    assertTrue(recovered.games().stream().allMatch(g -> g.collectionStatus() == NOT_DUE));
    assertTrue(briefings.findAll().stream().allMatch(b -> b.getGenerationStatus() == BriefingGenerationStatus.SUCCEEDED));
    verify(ai, times(4)).generate(any()); verify(steam, times(1)).fetch(APP, 5);
  }

  @Test void oldUnbriefedNewsOutsideLatestWindowIsDrainedInBoundedBatches() {
    var now = LocalDateTime.now(ZoneOffset.UTC);
    writer.save(APP, LongStream.rangeClosed(1, 7).mapToObj(id -> item(APP, Long.toString(id), "old")).toList(), now, now);
    when(steam.fetch(OTHER, 5)).thenReturn(List.of());
    sync.runOnce(); assertEquals(5, briefings.count());
    sync.runOnce(); assertEquals(7, briefings.count());
    verify(ai, times(7)).generate(any()); verify(steam, never()).fetch(APP, 5);
  }

  @Test void abandonedProcessingRecoversButActiveProcessingRemainsLeased() {
    sync.runOnce();
    db.update("update game_news_briefings set generation_status = 'PROCESSING', generated_from_hash = null, processing_started_at = ?", LocalDateTime.now(ZoneOffset.UTC));
    sync.runOnce(); verify(ai, times(2)).generate(any());
    db.update("update game_news_briefings set processing_started_at = ?", LocalDateTime.now(ZoneOffset.UTC).minusMinutes(11));
    sync.runOnce(); verify(ai, times(4)).generate(any());
    assertTrue(briefings.findAll().stream().allMatch(b -> b.getGenerationStatus() == BriefingGenerationStatus.SUCCEEDED));
  }

  @Test void twoWorkersClaimCollectionOnlyOnce() throws Exception {
    try (var pool = Executors.newFixedThreadPool(2)) {
      var start = new CountDownLatch(1);
      Callable<Optional<GameNewsCollectionResult>> task = () -> { start.await(); return collector.collectIfDue(APP, 5); };
      var first = pool.submit(task); var second = pool.submit(task); start.countDown();
      assertEquals(1, (first.get(10, TimeUnit.SECONDS).isPresent() ? 1 : 0) + (second.get(10, TimeUnit.SECONDS).isPresent() ? 1 : 0));
      verify(steam, times(1)).fetch(APP, 5);
    }
  }

  @Test void expiredCollectionLeaseRecoversAndRejectsLateOldResponse() {
    var now = LocalDateTime.now(ZoneOffset.UTC);
    var old = writer.claimDueCollection(APP, now.minusMinutes(11));
    assertNotNull(old);
    assertNull(writer.claimDueCollection(APP, now.minusMinutes(10)));
    var current = writer.claimDueCollection(APP, now);
    assertNotNull(current);
    assertTrue(writer.save(APP, List.of(item(APP, "1", "stale")), old, now).stale());
    assertEquals(0, news.count());
    assertFalse(writer.save(APP, List.of(item(APP, "1", "fresh")), current, now).stale());
    assertEquals(1, news.count());
    assertTrue(states.findById(APP).orElseThrow().getNextCollectAt().isAfter(now.plusHours(5)));
  }

  private void due(long appId) { db.update("update game_news_sync_state set next_collect_at = ? where app_id = ?", LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1), appId); }
  private SteamNewsItem item(long appId, String id, String content) {
    return new SteamNewsItem(appId, id, "Title " + id, content, "https://steamcommunity.com/news/" + id, LocalDateTime.of(2026, 10, 20, 0, 0));
  }
}
