package org.ict.final_project_back_end.domain.game;

import jakarta.persistence.EntityManager;
import java.util.List;
import org.ict.final_project_back_end.domain.game.entity.Game;
import org.ict.final_project_back_end.domain.game.entity.GameSearchName;
import org.ict.final_project_back_end.domain.game.repository.GameRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BbsGameSearchIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired GameRepository games;
  @Autowired EntityManager em;
  private static final long DOTA = 4200000001L;

  @BeforeEach void fixtures() {
    var dota = games.save(Game.builder().appId(DOTA).name("Dota 2").genre("Action,Strategy")
        .thumbnailUrl("https://example.invalid/dota.jpg").aboutTheGame("KEEP CATALOG CONTENT")
        .shortDescription("KEEP SHORT DESCRIPTION").build());
    games.saveAll(List.of(
        Game.builder().appId(DOTA + 1).name("Dota 2 Workshop").build(),
        Game.builder().appId(DOTA + 2).name("100% Match").build(),
        Game.builder().appId(DOTA + 3).name("A_B Adventure").build(),
        Game.builder().appId(DOTA + 4).name("A!B Adventure").build()));
    em.persist(new GameSearchName(dota, "도타 2", "도타 2", GameSearchName.Kind.ALIAS, "test"));
    em.persist(new GameSearchName(dota, "도타 투", "도타 투", GameSearchName.Kind.ALIAS, "test"));
    em.flush();
  }

  @Test void publicAliasSearchDeduplicatesAndDoesNotModifyCatalog() throws Exception {
    long before = games.count();
    mvc.perform(get("/api/games/bbs/search").param("q", "도타"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].appId").value(DOTA))
        .andExpect(jsonPath("$.content[0].name").value("Dota 2"))
        .andExpect(jsonPath("$.content[0].aboutTheGame").doesNotExist())
        .andExpect(jsonPath("$.hasMore").value(false));
    em.flush(); em.clear();
    assertEquals(before, games.count());
    assertEquals("KEEP CATALOG CONTENT", games.findById(DOTA).orElseThrow().getAboutTheGame());
    assertEquals("KEEP SHORT DESCRIPTION", games.findById(DOTA).orElseThrow().getShortDescription());
  }

  @Test void unicodeAndCaseNormalizationKeepExactNameFirst() throws Exception {
    mvc.perform(get("/api/games/bbs/search").param("q", "  ＤＯＴＡ　２  ").param("limit", "1"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.query").value("dota 2"))
        .andExpect(jsonPath("$.content[0].appId").value(DOTA))
        .andExpect(jsonPath("$.content.length()").value(1)).andExpect(jsonPath("$.hasMore").value(true));
  }

  @Test void literalWildcardsDoNotReturnTheWholeCatalog() throws Exception {
    for (var entry : List.of(new String[]{"%", "100% Match"}, new String[]{"_", "A_B Adventure"}, new String[]{"!", "A!B Adventure"})) {
      mvc.perform(get("/api/games/bbs/search").param("q", entry[0]))
          .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
          .andExpect(jsonPath("$.content[0].name").value(entry[1]));
    }
  }

  @Test void inactiveAliasesAreNotUsed() throws Exception {
    em.createNativeQuery("update game_search_names set active = false where app_id = :id")
        .setParameter("id", DOTA).executeUpdate();
    mvc.perform(get("/api/games/bbs/search").param("q", "도타"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0));
  }

  @Test void appIdSearchAndSelectionReturnTheSameGame() throws Exception {
    mvc.perform(get("/api/games/bbs/search").param("q", Long.toString(DOTA)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].appId").value(DOTA));
    mvc.perform(get("/api/games/bbs/" + DOTA))
        .andExpect(status().isOk()).andExpect(jsonPath("$.appId").value(DOTA))
        .andExpect(jsonPath("$.genre").value("Action,Strategy"))
        .andExpect(jsonPath("$.thumbnailUrl").value("https://example.invalid/dota.jpg"));
    mvc.perform(get("/api/games/bbs/4294967295"))
        .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("GAME_NOT_FOUND"));
  }

  @Test void noResultsRemainAValidResponse() throws Exception {
    mvc.perform(get("/api/games/bbs/search").param("q", "no-match-bbs04-xyz"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0))
        .andExpect(jsonPath("$.hasMore").value(false));
  }

  @Test void rejectsBlankOversizeAndControlCharacterQueries() throws Exception {
    for (String query : List.of("", "   ", "a".repeat(256), "Dota\u0000")) {
      mvc.perform(get("/api/games/bbs/search").param("q", query))
          .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_GAME_SEARCH"));
    }
  }

  @Test void validatesLimitsAndAppIds() throws Exception {
    for (String limit : List.of("0", "21", "no-number")) {
      mvc.perform(get("/api/games/bbs/search").param("q", "dota").param("limit", limit))
          .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_GAME_SEARCH"));
    }
    for (String id : List.of("0", "-1", "4294967296", "not-a-number")) {
      mvc.perform(get("/api/games/bbs/" + id)).andExpect(status().isBadRequest());
    }
  }
}
