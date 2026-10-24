package org.ict.final_project_back_end.domain.game;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.ict.final_project_back_end.domain.game.entity.*;
import org.ict.final_project_back_end.domain.game.service.*;
import org.ict.final_project_back_end.domain.gamenews.client.NewsVoiceIntentAiClient;
import org.ict.final_project_back_end.domain.gamenews.entity.GameNewsSupport;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:multilingual-voice;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "app.game-search.sync-enabled=false", "app.seed.games.enabled=false", "app.featured.sync-on-startup=false"})
@AutoConfigureMockMvc @Transactional
class MultilingualVoiceGameIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired EntityManager em;
  @Autowired GameNameResolver resolver;
  @MockitoBean NewsVoiceIntentAiClient ai;
  @Autowired org.ict.final_project_back_end.domain.game.repository.GameRepository gameRows;
  @Autowired org.ict.final_project_back_end.domain.game.repository.GameSearchNameRepository nameRows;
  @Autowired org.springframework.transaction.support.TransactionTemplate tx;
  final ObjectMapper json = new ObjectMapper();
  @BeforeEach void setup() {
    game(570, "Dota 2"); game(730, "Counter-Strike 2");
    new GameSearchCatalogSync(gameRows, nameRows, tx).synchronize();
  }
  Game game(long id, String name) {
    var game = Game.builder().appId(id).name(name).build(); em.persist(game);
    em.persist(new GameSearchName(game, name, GameNameNormalizer.normalize(name), GameSearchName.Kind.CANONICAL, "test"));
    em.persist(new GameNewsSupport(game, GameNewsSupport.Status.SUPPORTED, "test"));
    em.flush(); return game;
  }
  @Test void koreanAndEnglishDotaSpellingsResolveToSameDatabaseId() throws Exception {
    for (String text : List.of("도타 투", "도타2", "도타 2", "Dota two", "DOTA 2", "Dota2")) {
      selected(text, 570); intent(text + " 업데이트 알려 줘", 570);
    }
    verifyNoInteractions(ai);
  }
  @Test void koreanAndEnglishCounterStrikeSpellingsResolveToSameDatabaseId() throws Exception {
    for (String text : List.of("카운터스트라이크2", "카운터 스트라이크 투", "CounterStrike 2", "Counter-Strike 2", "씨에스 투")) {
      selected(text, 730); intent(text + " 패치 알려줘", 730);
    }
    intent("Please show me CounterStrike 2 updates", 730);
    intent("Dota two patch please", 570);
    verifyNoInteractions(ai);
  }
  @Test void misheardKoreanProducesCandidateAndRequiresConfirmation() throws Exception {
    var result = resolver.resolve("오타투");
    assertFalse(result.autoSelect()); assertEquals(570, result.candidates().getFirst().appId());
    var response = mvc.perform(post("/api/games/news/voice-intent").contentType("application/json")
        .content(json.writeValueAsString(Map.of("transcript", "오타투 업데이트 알려 줘", "version", 1))))
        .andExpect(status().isOk()).andExpect(jsonPath("$.action").value("CHOOSE_GAME"))
        .andExpect(jsonPath("$.candidates[0].appId").value(570)).andReturn().getResponse();
    var body = json.readTree(response.getContentAsString());
    var context = Map.of("category", "PATCH", "allCategories", false, "awaiting", "GAME",
        "candidateSetToken", body.get("candidateSetToken").asText(), "candidateVersion", body.get("candidateVersion").asLong());
    mvc.perform(post("/api/games/news/voice-intent").cookie(response.getCookies()).contentType("application/json")
        .content(json.writeValueAsString(Map.of("transcript", "1번", "version", 2, "context", context))))
        .andExpect(status().isOk()).andExpect(jsonPath("$.action").value("SEARCH"))
        .andExpect(jsonPath("$.appId").value(570)).andExpect(jsonPath("$.category").value("PATCH"));
    verifyNoInteractions(ai);
  }
  @Test void sequelNumbersAndUnrelatedNamesNeverAutoMapToKnownGames() {
    assertTrue(resolver.resolve("도타 3").candidates().isEmpty());
    assertTrue(resolver.resolve("CounterStrike 20").candidates().isEmpty());
    assertTrue(resolver.resolve("completely unrelated game").candidates().isEmpty());
  }
  @Test void dictionaryAliasCollisionWithAnotherDbGameRequiresChoice() {
    game(123450, "도타 투");
    var result = resolver.resolve("도타 투");
    assertFalse(result.autoSelect()); assertEquals(2, result.candidates().size());
  }
  void selected(String text, long id) throws Exception {
    mvc.perform(post("/api/games/resolve").contentType("application/json")
        .content(json.writeValueAsString(Map.of("gameQuery", text, "version", 1))))
        .andExpect(status().isOk()).andExpect(jsonPath("$.action").value("SELECTED"))
        .andExpect(jsonPath("$.appId").value(id));
  }
  void intent(String text, long id) throws Exception {
    mvc.perform(post("/api/games/news/voice-intent").contentType("application/json")
        .content(json.writeValueAsString(Map.of("transcript", text, "version", 1))))
        .andExpect(status().isOk()).andExpect(jsonPath("$.action").value("SEARCH"))
        .andExpect(jsonPath("$.appId").value(id)).andExpect(jsonPath("$.category").value("PATCH"));
  }
}
