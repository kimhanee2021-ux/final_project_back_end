package org.ict.final_project_back_end.domain.gamenews.service;

import org.ict.final_project_back_end.domain.gamenews.exception.SteamNewsException;

import org.ict.final_project_back_end.domain.game.entity.Game;
import org.ict.final_project_back_end.domain.game.repository.GameRepository;
import org.ict.final_project_back_end.domain.gamenews.client.*;
import org.ict.final_project_back_end.domain.gamenews.dto.*;
import org.ict.final_project_back_end.domain.gamenews.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(showSql = false, properties = {
    "spring.test.database.replace=NONE",
    "spring.datasource.url=jdbc:h2:mem:news-collection-tests;MODE=MySQL;DB_CLOSE_DELAY=-1"
})
@Import({GameNewsCollectionService.class, GameNewsWriter.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GameNewsCollectionServiceTest {
  private static final long APP = 900101L;
  private static final LocalDateTime TIME = LocalDateTime.of(2026, 10, 24, 0, 0);
  @Autowired GameRepository games;
  @Autowired GameNewsRepository news;
  @Autowired GameNewsSyncStateRepository states;
  @Autowired GameNewsBriefingRepository briefings;
  @Autowired GameNewsCollectionService service;
  @Autowired GameNewsWriter writer;
  @MockitoBean SteamNewsClient client;

  @BeforeEach
  void setup() {
    briefings.deleteAllInBatch();
    news.deleteAllInBatch();
    states.deleteAllInBatch();
    games.deleteAllInBatch();
    games.saveAndFlush(Game.builder().appId(APP).name("Collection test game").build());
  }

  @Test
  void collectsOutsideTransactionAndRepeatsWithoutDuplicatesOrAiRows() {
    when(client.fetch(APP, 5)).thenAnswer(call -> {
      assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
      return List.of(item(APP, "1", "original"));
    });
    var first = service.collect(APP, 5);
    var stored = news.findByGame_AppIdAndSourceNewsId(APP, "1").orElseThrow();
    var second = service.collect(APP, 5);
    var checked = news.findById(stored.getId()).orElseThrow();
    assertEquals(1, first.inserted());
    assertEquals(0, second.inserted());
    assertEquals(1, second.unchanged());
    assertEquals(1, news.count());
    assertEquals(stored.getUpdatedAt(), checked.getUpdatedAt());
    assertEquals(stored.getCollectedAt(), checked.getCollectedAt());
    assertFalse(checked.getLastSeenAt().isBefore(stored.getLastSeenAt()));
    assertEquals(0, briefings.count());
    var state = states.findById(APP).orElseThrow();
    assertNotNull(state.getLastSuccessAt());
    assertEquals(state.getLastSuccessAt().plusHours(6), state.getNextCollectAt());
    assertEquals(0, state.getConsecutiveFailures());
  }

  @Test
  void changesUpdateSameRowAndRetainOriginalCollectionTimestamp() {
    writer.save(APP, List.of(item(APP, "1", "original")), TIME, TIME);
    var old = news.findByGame_AppIdAndSourceNewsId(APP, "1").orElseThrow();
    var result = writer.save(APP, List.of(item(APP, "1", "edited")), TIME.plusMinutes(1), TIME.plusMinutes(2));
    var changed = news.findById(old.getId()).orElseThrow();
    assertEquals(1, result.updated());
    assertEquals(1, news.count());
    assertEquals("edited", changed.getContent());
    assertNotEquals(old.getContentHash(), changed.getContentHash());
    assertEquals(TIME, changed.getCollectedAt());
    assertEquals(TIME.plusMinutes(2), changed.getUpdatedAt());
    assertEquals(TIME.plusMinutes(2), changed.getLastSeenAt());
  }

  @Test
  void recordsEmptySuccessfulCollectionWithoutCreatingNews() {
    when(client.fetch(APP, 5)).thenReturn(List.of());
    var result = service.collect(APP, 5);
    assertEquals(0, result.received());
    assertEquals(0, news.count());
    assertNotNull(states.findById(APP).orElseThrow().getLastSuccessAt());
  }

  @Test
  void missingGameNeverCallsSteamOrCreatesState() {
    assertThrows(IllegalArgumentException.class, () -> service.collect(999999L, 5));
    verifyNoInteractions(client);
    assertEquals(0, states.count());
  }

  @Test
  void failuresKeepLastSuccessBackOffAndResetAfterRecovery() {
    writer.save(APP, List.of(item(APP, "1", "original")), TIME.minusDays(1), TIME.minusDays(1));
    when(client.fetch(APP, 5)).thenThrow(new SteamNewsException("Steam HTTP 429"));
    assertThrows(SteamNewsException.class, () -> service.collect(APP, 5));
    var first = states.findById(APP).orElseThrow();
    assertEquals(1, first.getConsecutiveFailures());
    assertEquals(TIME.minusDays(1), first.getLastSuccessAt());
    assertEquals("Steam HTTP 429", first.getLastError());
    assertTrue(first.getNextCollectAt().isAfter(first.getLastAttemptAt()));
    assertThrows(SteamNewsException.class, () -> service.collect(APP, 5));
    assertEquals(2, states.findById(APP).orElseThrow().getConsecutiveFailures());
    assertEquals(1, news.count());
    doReturn(List.of(item(APP, "1", "original"))).when(client).fetch(APP, 5);
    service.collect(APP, 5);
    var recovered = states.findById(APP).orElseThrow();
    assertEquals(0, recovered.getConsecutiveFailures());
    assertNull(recovered.getLastError());
    assertTrue(recovered.getLastSuccessAt().isAfter(first.getLastSuccessAt()));
  }

  @Test
  void rollsBackEntireGameWhenAnItemIsInvalidAndRecordsFailureSeparately() {
    when(client.fetch(APP, 5)).thenReturn(List.of(item(APP, "1", "valid"), item(730L, "2", "wrong game")));
    assertThrows(IllegalArgumentException.class, () -> service.collect(APP, 5));
    assertEquals(0, news.count());
    var state = states.findById(APP).orElseThrow();
    assertEquals(1, state.getConsecutiveFailures());
    assertNull(state.getLastSuccessAt());
  }

  @Test
  void olderResponseOrFailureCannotOverwriteLaterCollection() {
    writer.save(APP, List.of(item(APP, "1", "newer")), TIME.plusMinutes(1), TIME.plusMinutes(2));
    var stale = writer.save(APP, List.of(item(APP, "1", "older")), TIME, TIME.plusMinutes(3));
    assertTrue(stale.stale());
    writer.recordFailure(APP, TIME, TIME.plusMinutes(4), "old failure");
    assertEquals("newer", news.findByGame_AppIdAndSourceNewsId(APP, "1").orElseThrow().getContent());
    assertEquals(0, states.findById(APP).orElseThrow().getConsecutiveFailures());
    assertEquals(TIME.plusMinutes(2), states.findById(APP).orElseThrow().getLastSuccessAt());
  }

  @Test
  void concurrentFirstCollectionsUseOneNewsRowAndOneStateRow() throws Exception {
    try (var pool = Executors.newFixedThreadPool(2)) {
      var start = new CountDownLatch(1);
      Callable<GameNewsCollectionResult> work = () -> {
        assertTrue(start.await(5, TimeUnit.SECONDS));
        return writer.save(APP, List.of(item(APP, "1", "same")), TIME, TIME);
      };
      var first = pool.submit(work);
      var second = pool.submit(work);
      start.countDown();
      var a = first.get(10, TimeUnit.SECONDS);
      var b = second.get(10, TimeUnit.SECONDS);
      assertEquals(1, a.inserted() + b.inserted());
      assertEquals(1, a.unchanged() + b.unchanged());
      assertEquals(1, news.count());
      assertEquals(1, states.count());
    }
  }

  @Test
  void failureDelayGrowsAndIsCappedWithoutErasingPreviousSuccess() {
    writer.save(APP, List.of(), TIME, TIME);
    for (int i = 1; i <= 9; i++) {
      var at = TIME.plusMinutes(i);
      writer.recordFailure(APP, at, at, "test failure");
      var state = states.findById(APP).orElseThrow();
      assertEquals(at.plusMinutes(Math.min(360L, 5L << (i - 1))), state.getNextCollectAt());
      assertEquals(TIME, state.getLastSuccessAt());
    }
  }

  private SteamNewsItem item(long appId, String id, String content) {
    return new SteamNewsItem(appId, id, "Title", content, "https://steamcommunity.com/news/" + id, TIME);
  }
}
