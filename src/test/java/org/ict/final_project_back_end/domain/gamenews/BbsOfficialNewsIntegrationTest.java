package org.ict.final_project_back_end.domain.gamenews;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import jakarta.persistence.EntityManager;
import org.ict.final_project_back_end.domain.game.entity.Game;
import org.ict.final_project_back_end.domain.game.repository.GameRepository;
import org.ict.final_project_back_end.domain.gamenews.client.SteamNewsClient;
import org.ict.final_project_back_end.domain.gamenews.dto.SteamNewsItem;
import org.ict.final_project_back_end.domain.gamenews.exception.SteamNewsException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BbsOfficialNewsIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired GameRepository games;
  @Autowired EntityManager em;
  @MockitoBean SteamNewsClient steam;
  static final AtomicLong IDS = new AtomicLong(4200010000L);
  long appId;
  @BeforeEach void fixture() {
    appId = IDS.incrementAndGet();
    games.saveAndFlush(Game.builder().appId(appId).name("BBS Test Game").aboutTheGame("PRESERVE").build());
  }

  @Test void publicReadReturnsOriginalOfficialContentAndDoesNotChangeCatalog() throws Exception {
    long count = games.count();
    when(steam.fetch(appId, 40)).thenReturn(List.of(new SteamNewsItem(appId, "18446744073709551615",
        "Counter-Strike 2 Update", "[p]Official text[/p]", "https://steamcommunity.com/a",
        LocalDateTime.of(2026, 10, 24, 0, 0), List.of("patchnotes"))));
    mvc.perform(get("/api/games/bbs/" + appId + "/news")).andExpect(status().isOk())
        .andExpect(jsonPath("$.appId").value(appId)).andExpect(jsonPath("$.stale").value(false))
        .andExpect(jsonPath("$.items[0].id").value("18446744073709551615"))
        .andExpect(jsonPath("$.items[0].category").value("PATCH"))
        .andExpect(jsonPath("$.items[0].content").value("Official text"))
        .andExpect(jsonPath("$.items[0].publishedAt").value("2026-10-24T00:00:00Z"))
        .andExpect(jsonPath("$.items[0].feedName").value("steam_community_announcements"));
    em.flush(); em.clear();
    assertEquals(count, games.count());
    assertEquals("PRESERVE", games.findById(appId).orElseThrow().getAboutTheGame());
  }

  @Test void handlesNoNewsAndFailuresSeparately() throws Exception {
    when(steam.fetch(appId, 40)).thenReturn(List.of());
    mvc.perform(get("/api/games/bbs/" + appId + "/news")).andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(0));
    long second = IDS.incrementAndGet();
    games.saveAndFlush(Game.builder().appId(second).name("Failure Game").build());
    when(steam.fetch(second, 40)).thenThrow(new SteamNewsException("upstream failure"));
    mvc.perform(get("/api/games/bbs/" + second + "/news")).andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("STEAM_NEWS_UNAVAILABLE"));
  }

  @Test void rejectsInvalidAndUnknownGamesBeforeNetworkAccess() throws Exception {
    for (String id : List.of("0", "-1", "4294967296", "not-a-number"))
      mvc.perform(get("/api/games/bbs/" + id + "/news")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/games/bbs/4294967295/news")).andExpect(status().isNotFound());
    verifyNoInteractions(steam);
  }
}
