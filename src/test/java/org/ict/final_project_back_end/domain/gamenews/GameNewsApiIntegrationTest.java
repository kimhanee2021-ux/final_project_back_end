package org.ict.final_project_back_end.domain.gamenews;

import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.ict.final_project_back_end.domain.game.entity.Game;
import org.ict.final_project_back_end.domain.gamenews.client.NewsBriefingAiClient;
import org.ict.final_project_back_end.domain.gamenews.client.SteamNewsClient;
import org.ict.final_project_back_end.domain.gamenews.entity.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:news-api-tests;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.jpa.properties.hibernate.generate_statistics=true",
    "logging.level.org.hibernate.stat=OFF", "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"})
@AutoConfigureMockMvc
@Transactional
class GameNewsApiIntegrationTest {
  static final long APP = 920001L, OTHER_APP = 920002L, EMPTY_APP = 920003L;
  static final LocalDateTime TIME = LocalDateTime.of(2026, 10, 24, 0, 0);
  static final String HASH = "a".repeat(64);
  @Autowired MockMvc mvc;
  @Autowired EntityManager em;
  @MockitoBean SteamNewsClient steam;
  @MockitoBean NewsBriefingAiClient ai;
  long older, event, newer, otherGame, stale, nullDate, pending, failed, rawOnly;

  @BeforeEach void setup() {
    var game = Game.builder().appId(APP).name("First game").build(); em.persist(game);
    var other = Game.builder().appId(OTHER_APP).name("Other game").build(); em.persist(other);
    em.persist(Game.builder().appId(EMPTY_APP).name("Empty game").build());
    older = add(game, "old", TIME, GameNewsCategory.PATCH, true, BriefingGenerationStatus.SUCCEEDED, HASH);
    event = add(game, "event", TIME.plusDays(1), GameNewsCategory.EVENT, true, BriefingGenerationStatus.SUCCEEDED, HASH);
    newer = add(game, "new", TIME.plusDays(1), GameNewsCategory.PATCH, true, BriefingGenerationStatus.SUCCEEDED, HASH);
    otherGame = add(other, "other", TIME.plusDays(2), GameNewsCategory.NOTICE, true, BriefingGenerationStatus.SUCCEEDED, HASH);
    stale = add(game, "stale", TIME.minusDays(1), GameNewsCategory.MAINTENANCE, true, BriefingGenerationStatus.FAILED, "b".repeat(64));
    nullDate = add(game, "null-date", null, GameNewsCategory.OTHER, true, BriefingGenerationStatus.SUCCEEDED, HASH);
    pending = add(game, "pending", TIME.plusDays(4), null, false, BriefingGenerationStatus.PROCESSING, null);
    failed = add(game, "failed", TIME.plusDays(4), null, false, BriefingGenerationStatus.FAILED, null);
    rawOnly = raw(game, "raw-only", TIME).getId();
    em.flush(); em.clear();
  }

  @AfterEach void noExternalCalls() {
    verify(steam, never()).fetch(anyLong(), anyInt());
    verify(ai, never()).generate(any());
  }

  @Test void publicListReturnsStablePagingMetadataAndNoUnpublishedRows() throws Exception {
    mvc.perform(get("/api/games/news").param("size", "2"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(6))
        .andExpect(jsonPath("$.totalPages").value(3)).andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(2)).andExpect(jsonPath("$.first").value(true))
        .andExpect(jsonPath("$.last").value(false))
        .andExpect(jsonPath("$.content[0].newsId").value(otherGame))
        .andExpect(jsonPath("$.content[1].newsId").value(newer));
    mvc.perform(get("/api/games/news").param("page", "1").param("size", "2"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].newsId").value(event))
        .andExpect(jsonPath("$.content[1].newsId").value(older));
    mvc.perform(get("/api/games/news").param("page", "2").param("size", "2"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.last").value(true))
        .andExpect(jsonPath("$.content[1].newsId").value(nullDate));
  }

  @Test void gamePathAndAppIdFilterReturnSameGameOnly() throws Exception {
    var byPath = mvc.perform(get("/api/games/" + APP + "/news")).andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(5)).andReturn().getResponse().getContentAsString();
    var byFilter = mvc.perform(get("/api/games/news").param("appId", Long.toString(APP)))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    assertEquals(byPath, byFilter);
    assertFalse(byPath.contains("Other game"));
  }

  @Test void categoryFilterCombinesWithGameAndNormalizesCase() throws Exception {
    mvc.perform(get("/api/games/" + APP + "/news").param("category", " patch ").param("size", "1"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.content[0].newsId").value(newer));
    for (var category : GameNewsCategory.values()) {
      mvc.perform(get("/api/games/news").param("category", category.name())).andExpect(status().isOk());
    }
    mvc.perform(get("/api/games/" + APP + "/news").param("category", "NOTICE"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test void detailHasKoreanBodyAndUtcDatesWithoutInternalFieldsOrRawSource() throws Exception {
    mvc.perform(get("/api/games/" + APP + "/news/" + older))
        .andExpect(status().isOk()).andExpect(jsonPath("$.newsId").value(older))
        .andExpect(jsonPath("$.appId").value(APP)).andExpect(jsonPath("$.gameName").value("First game"))
        .andExpect(jsonPath("$.aiContent").value("한국어 브리핑 본문입니다."))
        .andExpect(jsonPath("$.publishedAt").value("2026-10-24T00:00:00Z"))
        .andExpect(jsonPath("$.generatedAt").value("2026-10-24T01:00:00Z"))
        .andExpect(jsonPath("$.sourceUpdatedAt").value("2026-10-24T00:00:00Z"))
        .andExpect(jsonPath("$.sourceUrl").value("https://steamcommunity.com/news/old"))
        .andExpect(jsonPath("$.stale").value(false))
        .andExpect(jsonPath("$.generationStatus").value("SUCCEEDED"))
        .andExpect(jsonPath("$.content").doesNotExist()).andExpect(jsonPath("$.contentHash").doesNotExist())
        .andExpect(jsonPath("$.generatedFromHash").doesNotExist()).andExpect(jsonPath("$.lastError").doesNotExist())
        .andExpect(jsonPath("$.modelName").doesNotExist()).andExpect(jsonPath("$.retryCount").doesNotExist());
    mvc.perform(get("/api/games/" + APP + "/news"))
        .andExpect(jsonPath("$.content[0].aiContent").doesNotExist())
        .andExpect(jsonPath("$.content[0].content").doesNotExist());
  }

  @Test void previousSuccessRemainsVisibleAndIndicatesStaleAfterRegenerationFailure() throws Exception {
    mvc.perform(get("/api/games/" + APP + "/news/" + stale))
        .andExpect(status().isOk()).andExpect(jsonPath("$.generationStatus").value("FAILED"))
        .andExpect(jsonPath("$.stale").value(true)).andExpect(jsonPath("$.aiContent").value("한국어 브리핑 본문입니다."));
  }

  @Test void initialPendingFailureRawOnlyAndWrongGameAreNotPublicDetails() throws Exception {
    for (long id : List.of(pending, failed, rawOnly, otherGame, Long.MAX_VALUE)) {
      mvc.perform(get("/api/games/" + APP + "/news/" + id)).andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("GAME_NEWS_NOT_FOUND"));
    }
  }

  @Test void missingGameHasDistinct404AndExistingEmptyGameHasEmptyPage() throws Exception {
    for (String path : List.of("/api/games/999999/news", "/api/games/999999/news/" + older, "/api/games/news?appId=999999")) {
      mvc.perform(get(path)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("GAME_NOT_FOUND"));
    }
    mvc.perform(get("/api/games/" + EMPTY_APP + "/news")).andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(0)).andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(20)).andExpect(jsonPath("$.totalElements").value(0))
        .andExpect(jsonPath("$.totalPages").value(0)).andExpect(jsonPath("$.first").value(true))
        .andExpect(jsonPath("$.last").value(true));
  }

  @Test void beyondLastPageIsEmptyAndBlankCategoryMeansAll() throws Exception {
    mvc.perform(get("/api/games/news").param("page", "20").param("size", "2").param("category", " "))
        .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0))
        .andExpect(jsonPath("$.totalElements").value(6)).andExpect(jsonPath("$.last").value(true));
  }

  @Test void rejectsInvalidFiltersNumbersRangesAndExcessiveOffsetsWithConsistentErrors() throws Exception {
    String[][] cases = {{"page", "-1"}, {"page", "bad"}, {"page", "2147483648"},
        {"size", "0"}, {"size", "101"}, {"size", "bad"}, {"appId", "0"}, {"appId", "-1"},
        {"appId", "4294967296"}, {"appId", "bad"}, {"category", "UNKNOWN"}, {"category", "a".repeat(31)}};
    for (var item : cases) {
      mvc.perform(get("/api/games/news").param(item[0], item[1])).andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("INVALID_GAME_NEWS_QUERY"));
    }
    mvc.perform(get("/api/games/news").param("page", "2147483647").param("size", "100"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_GAME_NEWS_QUERY"));
    for (String path : List.of("/api/games/nope/news", "/api/games/0/news", "/api/games/" + APP + "/news/0", "/api/games/" + APP + "/news/nope")) {
      mvc.perform(get(path)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_GAME_NEWS_QUERY"));
    }
  }

  @Test void mutationsAreNotMappedAndReadsDoNotModifyGenerationState() throws Exception {
    long before = ((Number) em.createQuery("select count(b) from GameNewsBriefing b").getSingleResult()).longValue();
    for (String path : List.of("/api/games/news", "/api/games/" + APP + "/news", "/api/games/" + APP + "/news/" + older)) {
      mvc.perform(post(path).contentType("application/json").content("{}")).andExpect(status().isMethodNotAllowed());
      mvc.perform(put(path).contentType("application/json").content("{}")).andExpect(status().isMethodNotAllowed());
      mvc.perform(patch(path).contentType("application/json").content("{}")).andExpect(status().isMethodNotAllowed());
      mvc.perform(delete(path)).andExpect(status().isMethodNotAllowed());
    }
    mvc.perform(get("/api/games/" + APP + "/news/" + older)).andExpect(status().isOk());
    assertEquals(before, ((Number)em.createQuery("select count(b) from GameNewsBriefing b").getSingleResult()).longValue());
    assertEquals(TIME.plusHours(1), em.find(GameNewsBriefing.class, older).getGeneratedAt());
  }

  @Test void listUsesBoundedQueriesWithOpenInViewDisabled() throws Exception {
    var stats = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
    stats.clear();
    mvc.perform(get("/api/games/news").param("size", "2")).andExpect(status().isOk());
    assertTrue(stats.getPrepareStatementCount() <= 2, "List must not load one source/game per row");
  }

  private long add(Game game, String id, LocalDateTime published, GameNewsCategory category,
                   boolean success, BriefingGenerationStatus status, String generatedHash) {
    var source = raw(game, id, published);
    var b = GameNewsBriefing.builder().news(source).generationStatus(status).category(category)
        .summary(success ? "한국어 요약입니다." : null).aiContent(success ? "한국어 브리핑 본문입니다." : null)
        .generatedAt(success ? TIME.plusHours(1) : null).generatedFromHash(generatedHash)
        .modelName(success ? "private-model" : null).lastError(status == BriefingGenerationStatus.FAILED ? "PRIVATE_ERROR" : null).build();
    em.persist(b);
    return source.getId();
  }
  private GameNews raw(Game game, String id, LocalDateTime published) {
    var source = GameNews.builder().game(game).sourceNewsId(id).title(id).content("PRIVATE_RAW_TEXT")
        .sourceUrl("https://steamcommunity.com/news/" + id).publishedAt(published).contentHash(HASH)
        .collectedAt(TIME).updatedAt(TIME).lastSeenAt(TIME).build();
    em.persist(source); return source;
  }
}
