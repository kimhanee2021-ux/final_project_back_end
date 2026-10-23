package org.ict.final_project_back_end.domain.gamenews;

import jakarta.persistence.EntityManager;
import org.ict.final_project_back_end.domain.game.entity.Game;
import org.ict.final_project_back_end.domain.game.repository.GameRepository;
import org.ict.final_project_back_end.domain.gamenews.entity.*;
import org.ict.final_project_back_end.domain.gamenews.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql = false, properties = {
    "spring.test.database.replace=NONE",
    "spring.datasource.url=jdbc:h2:mem:game-news-tests;MODE=MySQL;DB_CLOSE_DELAY=-1"
})
class GameNewsRepositoryTest {
  private static final LocalDateTime TIME = LocalDateTime.of(2026, 10, 23, 0, 0);
  private static final String HASH = "a".repeat(64);
  @Autowired GameRepository games;
  @Autowired GameNewsRepository news;
  @Autowired GameNewsBriefingRepository briefings;
  @Autowired GameNewsSyncStateRepository syncStates;
  @Autowired EntityManager em;
  private Game firstGame;
  private Game secondGame;

  @BeforeEach
  void setUp() {
    firstGame = games.saveAndFlush(Game.builder().appId(900001L).name("First game").build());
    secondGame = games.saveAndFlush(Game.builder().appId(900002L).name("Second game").build());
  }

  @Test
  void storesSteamIdAsStringAndListsOnlyRequestedGameInStableOrder() {
    GameNews older = addNews(firstGame, "18446744073709551615", TIME);
    GameNews newer = addNews(firstGame, "second", TIME);
    addNews(secondGame, "18446744073709551615", TIME.plusDays(1));
    em.clear();

    var found = news.findByGame_AppIdAndSourceNewsId(firstGame.getAppId(), "18446744073709551615").orElseThrow();
    assertEquals(older.getId(), found.getId());
    assertEquals(firstGame.getAppId(), found.getAppId());
    var page = news.findByGame_AppIdOrderByPublishedAtDescIdDesc(firstGame.getAppId(), PageRequest.of(0, 1));
    assertEquals(2, page.getTotalElements());
    assertEquals(newer.getId(), page.getContent().getFirst().getId());
  }

  @Test
  void rejectsDuplicateSteamPostWithinOneGame() {
    addNews(firstGame, "same", TIME);
    assertThrows(DataIntegrityViolationException.class, () -> addNews(firstGame, "same", TIME));
  }

  @Test
  void rejectsNewsForGameMissingFromCatalog() {
    Game missing = games.getReferenceById(999999999L);
    assertThrows(DataIntegrityViolationException.class, () -> addNews(missing, "missing", TIME));
  }

  @Test
  void restrictsDeletingGameThatHasNews() {
    addNews(firstGame, "retained", TIME);
    Long appId = firstGame.getAppId();
    // 영속성 컨텍스트의 연관 객체 검사가 아닌 실제 DB 외래키 거부를 확인한다.
    em.clear();
    assertThrows(DataIntegrityViolationException.class, () -> {
      games.deleteById(appId);
      games.flush();
    });
  }

  @Test
  void sharedPrimaryKeysAndUtcDefaultsSurviveReload() {
    LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1);
    GameNews article = addNews(firstGame, "shared-ids", TIME);
    var briefing = briefings.saveAndFlush(GameNewsBriefing.builder().news(article).build());
    var state = syncStates.saveAndFlush(GameNewsSyncState.builder().game(firstGame).build());
    em.clear();

    assertEquals(article.getId(), briefing.getNewsId());
    assertEquals(firstGame.getAppId(), state.getAppId());
    var savedBriefing = briefings.findById(article.getId()).orElseThrow();
    assertEquals(article.getId(), savedBriefing.getNews().getId());
    assertEquals(BriefingGenerationStatus.PENDING, savedBriefing.getGenerationStatus());
    assertEquals(0, savedBriefing.getRetryCount());
    assertNull(savedBriefing.getAiContent());
    assertNull(savedBriefing.getGeneratedAt());
    var savedState = syncStates.findById(firstGame.getAppId()).orElseThrow();
    assertEquals(firstGame.getAppId(), savedState.getGame().getAppId());
    assertEquals(0, savedState.getConsecutiveFailures());
    assertFalse(savedState.getNextCollectAt().isBefore(before));
    assertFalse(savedState.getNextCollectAt().isAfter(LocalDateTime.now(ZoneOffset.UTC).plusSeconds(1)));
    var savedArticle = news.findById(article.getId()).orElseThrow();
    assertFalse(savedArticle.getCollectedAt().isBefore(before));
    assertEquals(savedArticle.getCollectedAt(), savedArticle.getUpdatedAt());
    assertEquals(savedArticle.getCollectedAt(), savedArticle.getLastSeenAt());
  }

  @Test
  void recheckingUnchangedSourceDoesNotChangeSourceUpdateTime() {
    GameNews article = news.saveAndFlush(newsBuilder(firstGame, "refresh", TIME)
        .collectedAt(TIME).lastSeenAt(TIME).updatedAt(TIME).build());
    LocalDateTime firstCheck = TIME.plusHours(1);
    assertFalse(article.refreshSource("Title", "Original content", "https://example.invalid/news/refresh",
        TIME, HASH, firstCheck));
    em.flush();
    em.clear();
    GameNews reloaded = news.findById(article.getId()).orElseThrow();
    assertEquals(TIME, reloaded.getUpdatedAt());
    assertEquals(firstCheck, reloaded.getLastSeenAt());

    LocalDateTime secondCheck = TIME.plusHours(2);
    assertTrue(reloaded.refreshSource("Updated title", "Changed content", "https://example.invalid/changed",
        TIME, "b".repeat(64), secondCheck));
    em.flush();
    em.clear();
    var changed = news.findById(article.getId()).orElseThrow();
    assertEquals("Changed content", changed.getContent());
    assertEquals(secondCheck, changed.getUpdatedAt());
    assertEquals(TIME, changed.getCollectedAt());
  }

  @Test
  void publishedQueryFiltersByGameAndCategoryAndKeepsPreviousSuccessfulResult() {
    GameNews patch = addNews(firstGame, "patch", TIME);
    GameNews previousSuccess = addNews(firstGame, "previous-success", TIME.plusHours(1));
    GameNews pending = addNews(firstGame, "pending", TIME.plusHours(2));
    GameNews otherGame = addNews(secondGame, "other", TIME.plusHours(3));
    briefings.saveAllAndFlush(List.of(
        published(patch, GameNewsCategory.PATCH, BriefingGenerationStatus.SUCCEEDED),
        published(previousSuccess, GameNewsCategory.EVENT, BriefingGenerationStatus.FAILED),
        GameNewsBriefing.builder().news(pending).build(),
        published(otherGame, GameNewsCategory.PATCH, BriefingGenerationStatus.SUCCEEDED)));
    em.clear();

    var firstPage = briefings.findPublished(firstGame.getAppId(), null, PageRequest.of(0, 1));
    assertEquals(2, firstPage.getTotalElements());
    assertEquals(previousSuccess.getId(), firstPage.getContent().getFirst().getNewsId());
    assertEquals("Previous successful briefing", firstPage.getContent().getFirst().getAiContent());
    assertEquals(patch.getId(), briefings.findPublished(firstGame.getAppId(), GameNewsCategory.PATCH,
        PageRequest.of(0, 20)).getContent().getFirst().getNewsId());
    assertEquals(2, briefings.findPublished(null, GameNewsCategory.PATCH, PageRequest.of(0, 20)).getTotalElements());
    assertEquals(3, briefings.findPublished(null, null, PageRequest.of(0, 20)).getTotalElements());
  }

  @Test
  void generationQueueExcludesFutureRetriesProcessingAndExhaustedFailures() {
    var pending = addNews(firstGame, "queue-pending", TIME);
    var ready = addNews(firstGame, "queue-ready", TIME);
    var future = addNews(firstGame, "queue-future", TIME);
    var processing = addNews(firstGame, "queue-processing", TIME);
    var exhausted = addNews(firstGame, "queue-exhausted", TIME);
    briefings.saveAllAndFlush(List.of(
        GameNewsBriefing.builder().news(pending).build(),
        GameNewsBriefing.builder().news(ready).generationStatus(BriefingGenerationStatus.FAILED).nextRetryAt(TIME).build(),
        GameNewsBriefing.builder().news(future).generationStatus(BriefingGenerationStatus.FAILED).nextRetryAt(TIME.plusMinutes(1)).build(),
        GameNewsBriefing.builder().news(processing).generationStatus(BriefingGenerationStatus.PROCESSING).build(),
        GameNewsBriefing.builder().news(exhausted).generationStatus(BriefingGenerationStatus.FAILED).build()));
    em.clear();
    assertEquals(List.of(pending.getId(), ready.getId()), briefings.findReadyForGeneration(TIME, PageRequest.of(0, 20))
        .stream().map(GameNewsBriefing::getNewsId).toList());
    assertEquals(1, briefings.findReadyForGeneration(TIME, PageRequest.of(0, 1)).size());
  }

  @Test
  void collectionQueueReturnsOnlyDueGames() {
    syncStates.saveAllAndFlush(List.of(
        GameNewsSyncState.builder().game(firstGame).nextCollectAt(TIME).build(),
        GameNewsSyncState.builder().game(secondGame).nextCollectAt(TIME.plusMinutes(1)).build()));
    em.clear();
    assertEquals(List.of(firstGame.getAppId()), syncStates
        .findByNextCollectAtLessThanEqualOrderByNextCollectAtAscAppIdAsc(TIME, PageRequest.of(0, 20))
        .stream().map(GameNewsSyncState::getAppId).toList());
  }

  private GameNews addNews(Game game, String sourceId, LocalDateTime publishedAt) {
    return news.saveAndFlush(newsBuilder(game, sourceId, publishedAt).build());
  }

  private GameNews.GameNewsBuilder newsBuilder(Game game, String sourceId, LocalDateTime publishedAt) {
    return GameNews.builder().game(game).sourceNewsId(sourceId).title("Title").content("Original content")
        .sourceUrl("https://example.invalid/news/" + sourceId).publishedAt(publishedAt).contentHash(HASH);
  }

  private GameNewsBriefing published(GameNews article, GameNewsCategory category, BriefingGenerationStatus status) {
    return GameNewsBriefing.builder().news(article).category(category).summary("Summary")
        .aiContent("Previous successful briefing").generationStatus(status).generatedAt(TIME)
        .generatedFromHash(HASH).modelName("test-model").promptVersion("v1").build();
  }
}
