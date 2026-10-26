package org.ict.final_project_back_end.domain.game;

import jakarta.persistence.EntityManager;
import java.util.List;
import org.ict.final_project_back_end.domain.game.entity.Game;
import org.ict.final_project_back_end.domain.game.service.GameSearchException;
import org.ict.final_project_back_end.domain.game.repository.GameRepository;
import org.ict.final_project_back_end.domain.game.service.BbsGameSearchService;
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

// BBS 게임 검색: 음성 명령(BbsCommandService)이 쓰는 이름 검색은 서비스로 검증하고,
// 화면이 쓰는 게임 선택 주소(/api/games/bbs/{appId})는 HTTP로 검증한다.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BbsGameSearchIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired GameRepository games;
  @Autowired BbsGameSearchService search;
  @Autowired EntityManager em;
  private static final long DOTA = 4200000001L;

  @BeforeEach void fixtures() {
    games.save(Game.builder().appId(DOTA).name("Dota 2").genre("Action,Strategy")
        .thumbnailUrl("https://example.invalid/dota.jpg").aboutTheGame("KEEP CATALOG CONTENT")
        .shortDescription("KEEP SHORT DESCRIPTION").build());
    games.saveAll(List.of(
        Game.builder().appId(DOTA + 1).name("Dota 2 Workshop").build(),
        Game.builder().appId(DOTA + 2).name("100% Match").build(),
        Game.builder().appId(DOTA + 3).name("A_B Adventure").build(),
        Game.builder().appId(DOTA + 4).name("A!B Adventure").build()));
    em.flush();
  }

  @Test void nameSearchDoesNotModifyCatalog() {
    long before = games.count();
    var result = search.search("dota 2 w", 12);
    assertEquals(1, result.content().size());
    assertEquals(DOTA + 1, result.content().get(0).appId());
    result = search.search("dota 2", 1);
    assertEquals(DOTA, result.content().get(0).appId());
    assertEquals("Dota 2", result.content().get(0).name());
    em.flush(); em.clear();
    assertEquals(before, games.count());
    assertEquals("KEEP CATALOG CONTENT", games.findById(DOTA).orElseThrow().getAboutTheGame());
    assertEquals("KEEP SHORT DESCRIPTION", games.findById(DOTA).orElseThrow().getShortDescription());
  }

  @Test void unicodeAndCaseNormalizationKeepExactNameFirst() {
    var result = search.search("  ＤＯＴＡ　２  ", 1);
    assertEquals("dota 2", result.query());
    assertEquals(DOTA, result.content().get(0).appId());
    assertEquals(1, result.content().size());
    assertTrue(result.hasMore());
  }

  @Test void literalWildcardsDoNotReturnTheWholeCatalog() {
    for (var entry : List.of(new String[]{"%", "100% Match"}, new String[]{"_", "A_B Adventure"}, new String[]{"!", "A!B Adventure"})) {
      var result = search.search(entry[0], 12);
      assertEquals(1, result.content().size());
      assertEquals(entry[1], result.content().get(0).name());
    }
  }


  @Test void appIdSearchAndSelectionReturnTheSameGame() throws Exception {
    assertEquals(DOTA, search.search(Long.toString(DOTA), 12).content().get(0).appId());
    mvc.perform(get("/api/games/bbs/" + DOTA))
        .andExpect(status().isOk()).andExpect(jsonPath("$.appId").value(DOTA))
        .andExpect(jsonPath("$.genre").value("Action,Strategy"))
        .andExpect(jsonPath("$.thumbnailUrl").value("https://example.invalid/dota.jpg"));
    mvc.perform(get("/api/games/bbs/4294967295"))
        .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("GAME_NOT_FOUND"));
  }

  @Test void noResultsRemainAValidResponse() {
    var result = search.search("no-match-bbs04-xyz", 12);
    assertEquals(0, result.content().size());
    assertFalse(result.hasMore());
  }

  @Test void rejectsBlankOversizeAndControlCharacterQueries() {
    for (String query : List.of("", "   ", "a".repeat(256), "Dota\u0000")) {
      assertThrows(GameSearchException.class, () -> search.search(query, 12), query);
    }
  }

  @Test void validatesLimitsAndAppIds() throws Exception {
    for (int limit : List.of(0, 21)) {
      assertThrows(GameSearchException.class, () -> search.search("dota", limit));
    }
    for (String id : List.of("0", "-1", "4294967296", "not-a-number")) {
      mvc.perform(get("/api/games/bbs/" + id)).andExpect(status().isBadRequest());
    }
  }
}
