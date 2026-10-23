package org.ict.final_project_back_end.domain.gamenews.service;

import org.ict.final_project_back_end.domain.gamenews.exception.BriefingGenerationException;

import org.ict.final_project_back_end.domain.game.entity.Game;
import org.ict.final_project_back_end.domain.game.repository.GameRepository;
import org.ict.final_project_back_end.domain.gamenews.client.*;
import org.ict.final_project_back_end.domain.gamenews.client.NewsBriefingAiClient.Draft;
import org.ict.final_project_back_end.domain.gamenews.dto.SteamNewsItem;
import org.ict.final_project_back_end.domain.gamenews.entity.*;
import org.ict.final_project_back_end.domain.gamenews.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;
import static org.ict.final_project_back_end.domain.gamenews.dto.NewsBriefingResult.Outcome.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

@DataJpaTest(showSql = false, properties = {"spring.test.database.replace=NONE",
    "spring.datasource.url=jdbc:h2:mem:news-briefing-tests;MODE=MySQL;DB_CLOSE_DELAY=-1"})
@Import({GameNewsBriefingService.class, GameNewsBriefingWriter.class, GameNewsWriter.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GameNewsBriefingServiceTest {
  static final long APP = 900201L;
  static final LocalDateTime TIME = LocalDateTime.of(2026, 10, 20, 0, 0);
  static final String MODEL = "test-model", PROMPT = "news-ko-v1";
  @Autowired GameRepository games;
  @Autowired GameNewsRepository news;
  @Autowired GameNewsBriefingRepository briefings;
  @Autowired GameNewsSyncStateRepository states;
  @Autowired GameNewsWriter sourceWriter;
  @Autowired GameNewsBriefingWriter writer;
  @Autowired GameNewsBriefingService service;
  @MockitoBean NewsBriefingAiClient ai;
  long newsId;

  @BeforeEach void setup() {
    briefings.deleteAllInBatch(); news.deleteAllInBatch(); states.deleteAllInBatch(); games.deleteAllInBatch();
    games.saveAndFlush(Game.builder().appId(APP).name("Test game").build());
    sourceWriter.save(APP, List.of(item("original")), TIME, TIME);
    newsId = news.findByGame_AppIdAndSourceNewsId(APP, "1").orElseThrow().getId();
    when(ai.modelName()).thenReturn(MODEL); when(ai.promptVersion()).thenReturn(PROMPT);
    when(ai.generate(any())).thenAnswer(call -> {
      assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
      return draft("첫 브리핑입니다.");
    });
  }

  @Test void savesKoreanBriefingAndSkipsUnchangedSourceWithoutAnotherAiCall() {
    assertEquals(GENERATED, service.generate(newsId).outcome());
    var first = briefings.findById(newsId).orElseThrow();
    assertEquals(BriefingGenerationStatus.SUCCEEDED, first.getGenerationStatus());
    assertEquals(news.findById(newsId).orElseThrow().getContentHash(), first.getGeneratedFromHash());
    assertEquals(MODEL, first.getModelName()); assertEquals(PROMPT, first.getPromptVersion());
    assertEquals(UP_TO_DATE, service.generate(newsId).outcome());
    verify(ai, times(1)).generate(any());
    assertEquals(first.getGeneratedAt(), briefings.findById(newsId).orElseThrow().getGeneratedAt());
    assertEquals(1, briefings.count());
  }

  @Test void sourceChangeQueuesRegenerationAndUpdatesSameBriefingRow() {
    service.generate(newsId);
    String oldHash = briefings.findById(newsId).orElseThrow().getGeneratedFromHash();
    sourceWriter.save(APP, List.of(item("changed")), TIME.plusHours(1), TIME.plusHours(1));
    var pending = briefings.findById(newsId).orElseThrow();
    assertEquals(BriefingGenerationStatus.PENDING, pending.getGenerationStatus());
    assertEquals("첫 브리핑입니다.", pending.getAiContent());
    when(ai.generate(any())).thenReturn(draft("변경된 원문 브리핑입니다."));
    assertEquals(GENERATED, service.generate(newsId).outcome());
    var changed = briefings.findById(newsId).orElseThrow();
    assertEquals(1, briefings.count()); assertNotEquals(oldHash, changed.getGeneratedFromHash());
    assertEquals(news.findById(newsId).orElseThrow().getContentHash(), changed.getGeneratedFromHash());
    assertEquals("변경된 원문 브리핑입니다.", changed.getAiContent());
  }

  @Test void sameSourceCollectionDoesNotInvalidateSuccessfulBriefing() {
    service.generate(newsId);
    sourceWriter.save(APP, List.of(item("original")), TIME.plusHours(1), TIME.plusHours(1));
    assertEquals(UP_TO_DATE, service.generate(newsId).outcome());
    verify(ai, times(1)).generate(any());
  }

  @Test void regenerationFailureKeepsOldSuccessAndWaitsBeforeRetry() {
    service.generate(newsId);
    var old = briefings.findById(newsId).orElseThrow();
    sourceWriter.save(APP, List.of(item("changed")), TIME.plusHours(1), TIME.plusHours(1));
    when(ai.generate(any())).thenThrow(new BriefingGenerationException("AI_RATE_LIMITED"));
    assertEquals(FAILED, service.generate(newsId).outcome());
    var failed = briefings.findById(newsId).orElseThrow();
    assertEquals(old.getAiContent(), failed.getAiContent());
    assertEquals(old.getGeneratedAt(), failed.getGeneratedAt());
    assertEquals(old.getGeneratedFromHash(), failed.getGeneratedFromHash());
    assertEquals(1, failed.getRetryCount()); assertEquals("AI_RATE_LIMITED", failed.getLastError());
    assertEquals(DEFERRED, service.generate(newsId).outcome());
    assertEquals(1, briefings.findPublished(APP, null, PageRequest.of(0, 10)).getTotalElements());
    var retry = writer.claim(newsId, MODEL, PROMPT, failed.getNextRetryAt());
    assertNotNull(retry.work());
    assertEquals(GENERATED, writer.complete(retry.work(), draft("복구된 브리핑입니다."), failed.getNextRetryAt().plusSeconds(1)));
    assertEquals(0, briefings.findById(newsId).orElseThrow().getRetryCount());
  }

  @Test void discardsAiReplyIfSourceChangesDuringGenerationAndCanRegenerate() {
    when(ai.generate(any())).thenAnswer(call -> {
      sourceWriter.save(APP, List.of(item("new version")), TIME.plusHours(1), TIME.plusHours(1));
      return draft("이전 원문의 브리핑입니다.");
    });
    assertEquals(STALE, service.generate(newsId).outcome());
    var pending = briefings.findById(newsId).orElseThrow();
    assertNull(pending.getAiContent()); assertEquals(BriefingGenerationStatus.PENDING, pending.getGenerationStatus());
    doReturn(draft("최신 원문의 브리핑입니다.")).when(ai).generate(any());
    assertEquals(GENERATED, service.generate(newsId).outcome());
  }

  @Test void firstFailureStoresNoInventedBriefing() {
    when(ai.generate(any())).thenThrow(new BriefingGenerationException("AI_INVALID_RESPONSE"));
    assertEquals(FAILED, service.generate(newsId).outcome());
    var row = briefings.findById(newsId).orElseThrow();
    assertNull(row.getAiContent()); assertNull(row.getGeneratedAt());
    assertEquals(0, briefings.findPublished(APP, null, PageRequest.of(0, 10)).getTotalElements());
  }

  @Test void concurrentClaimsOnlyAllowOneWorker() throws Exception {
    try (var pool = Executors.newFixedThreadPool(2)) {
      var start = new CountDownLatch(1);
      Callable<GameNewsBriefingWriter.Claim> task = () -> {
        assertTrue(start.await(5, TimeUnit.SECONDS));
        return writer.claim(newsId, MODEL, PROMPT, TIME);
      };
      var a = pool.submit(task); var b = pool.submit(task); start.countDown();
      var first = a.get(10, TimeUnit.SECONDS); var second = b.get(10, TimeUnit.SECONDS);
      assertEquals(1, (first.work() != null ? 1 : 0) + (second.work() != null ? 1 : 0));
      assertEquals(BUSY, first.work() == null ? first.skipped() : second.skipped());
      assertEquals(1, briefings.count());
    }
  }

  @Test void expiredLeaseCanBeReclaimedAndLateWorkerCannotFinishOrFailIt() {
    var old = writer.claim(newsId, MODEL, PROMPT, TIME).work();
    assertEquals(BUSY, writer.claim(newsId, MODEL, PROMPT, TIME.plusMinutes(9)).skipped());
    var replacement = writer.claim(newsId, MODEL, PROMPT, TIME.plusMinutes(10)).work();
    assertNotNull(replacement);
    assertEquals(STALE, writer.complete(old, draft("오래된 응답입니다."), TIME.plusMinutes(11)));
    assertEquals(STALE, writer.fail(old, TIME.plusMinutes(11), "AI_TIMEOUT"));
    assertEquals(GENERATED, writer.complete(replacement, draft("정상 응답입니다."), TIME.plusMinutes(12)));
  }

  @Test void modelAndPromptChangesInvalidatePriorResult() {
    service.generate(newsId);
    when(ai.promptVersion()).thenReturn("news-ko-v2");
    assertEquals(GENERATED, service.generate(newsId).outcome());
    when(ai.modelName()).thenReturn("new-model");
    assertEquals(GENERATED, service.generate(newsId).outcome());
    verify(ai, times(3)).generate(any());
  }

  @Test void attemptTokenIsUniqueEvenWhenClockHasNotAdvancedAfterSourceChange() {
    var old = writer.claim(newsId, MODEL, PROMPT, TIME).work();
    sourceWriter.save(APP, List.of(item("changed")), TIME.plusSeconds(1), TIME.plusSeconds(1));
    var next = writer.claim(newsId, MODEL, PROMPT, TIME).work();
    assertTrue(next.startedAt().isAfter(old.startedAt()));
    assertEquals(STALE, writer.fail(old, TIME.plusSeconds(2), "AI_TIMEOUT"));
    assertEquals(GENERATED, writer.complete(next, draft("새 응답입니다."), TIME.plusSeconds(3)));
  }

  @Test void listsOnlyRequestedGameAndRejectsMissingGameOrNewsBeforeAi() {
    assertEquals(List.of(newsId), service.latestNewsIds(APP, 5));
    assertThrows(IllegalArgumentException.class, () -> service.latestNewsIds(999999, 5));
    assertThrows(IllegalArgumentException.class, () -> service.generate(999999));
    verify(ai, never()).generate(any());
  }

  private Draft draft(String content) { return new Draft(GameNewsCategory.PATCH, "한국어 요약입니다.", content); }
  private SteamNewsItem item(String content) { return new SteamNewsItem(APP, "1", "Title", content, "https://steamcommunity.com/news/1", TIME); }
}
