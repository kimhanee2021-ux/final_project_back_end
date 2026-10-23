package org.ict.final_project_back_end.domain.game;

import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.ict.final_project_back_end.domain.game.entity.*;
import org.ict.final_project_back_end.domain.game.service.*;
import org.ict.final_project_back_end.domain.gamenews.entity.GameNewsSupport;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:game-resolution;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "app.game-search.sync-enabled=false", "app.seed.games.enabled=false", "app.featured.sync-on-startup=false"})
@AutoConfigureMockMvc @Transactional
class GameResolutionIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired EntityManager em;
  @Autowired GameNameResolver resolver;
  static final Cookie OWNER = new Cookie("gs_game_search", "11111111-1111-1111-1111-111111111111");
  @BeforeEach void setup() {
    Game one = game(901001L, "Demo 2"), two = game(901002L, "Demo 20"), three = game(901003L, "Shared");
    game(901004L, "Ｓｈａｒｅｄ");
    alias(one, "데모 2"); alias(one, "공유별칭"); alias(two, "공유별칭"); alias(three, "Demo 2");
    game(901005L, "Literal %_!");
    em.persist(new GameNewsSupport(two, GameNewsSupport.Status.UNSUPPORTED, "test"));
    em.flush(); em.clear();
  }
  Game game(long id, String name) {
    var game = Game.builder().appId(id).name(name).build(); em.persist(game);
    em.persist(new GameSearchName(game, name, GameNameNormalizer.normalize(name), GameSearchName.Kind.CANONICAL, "games"));
    return game;
  }
  void alias(Game game, String alias) {
    em.persist(new GameSearchName(game, alias, GameNameNormalizer.normalize(alias), GameSearchName.Kind.ALIAS, "test"));
  }
  ResultActions postBody(String body, Cookie cookie) throws Exception {
    return mvc.perform(post("/api/games/resolve").cookie(cookie).contentType("application/json").content(body));
  }
  @Test void exactAliasSelectsDbIdentityWithUnknownSupportAndNoStoredBriefing() throws Exception {
    postBody("{\"gameQuery\":\"　데모 ２  \",\"version\":1}", OWNER).andExpect(status().isOk())
        .andExpect(jsonPath("$.action").value("SELECTED")).andExpect(jsonPath("$.appId").value(901001))
        .andExpect(jsonPath("$.support").value("UNKNOWN")).andExpect(jsonPath("$.briefing").value("NO_STORED_BRIEFING"));
  }
  @Test void collisionsIncludingCanonicalAgainstAliasNeverAutoSelect() {
    assertFalse(resolver.resolve("Demo 2").autoSelect());
    assertFalse(resolver.resolve("Shared").autoSelect());
    assertFalse(resolver.resolve("공유별칭").autoSelect());
    assertFalse(resolver.resolve("Demo 20 ").candidates().isEmpty());
    assertFalse(resolver.resolve("Literal").autoSelect());
    assertTrue(resolver.resolve("Literal %_!").autoSelect());
  }
  @Test void candidateOrderIsStableAndSelectionUsesServerSetAndIsSingleUse() throws Exception {
    var response = postBody("{\"gameQuery\":\"공유별칭\",\"version\":5}", OWNER)
        .andExpect(status().isOk()).andExpect(jsonPath("$.action").value("CHOOSE_GAME"))
        .andExpect(jsonPath("$.candidates[0].appId").value(901001)).andReturn();
    String token = token(response);
    String select = "{\"candidateSetToken\":\"" + token + "\",\"selectedCandidateIndex\":2,\"version\":5}";
    postBody(select, new Cookie("gs_game_search", "22222222-2222-2222-2222-222222222222")).andExpect(status().isConflict());
    postBody(select.replace("\"version\":5", "\"version\":4"), OWNER).andExpect(status().isConflict());
    postBody(select.replace("Index\":2", "Index\":9"), OWNER).andExpect(status().isBadRequest());
    postBody(select, OWNER).andExpect(status().isOk()).andExpect(jsonPath("$.appId").value(901002))
        .andExpect(jsonPath("$.support").value("UNSUPPORTED"));
    postBody(select, OWNER).andExpect(status().isConflict());
  }
  @Test void newSearchInvalidatesPriorCandidatesAndIdInjectionIsRejected() throws Exception {
    var old = postBody("{\"gameQuery\":\"Shared\",\"version\":3}", OWNER).andReturn();
    postBody("{\"gameQuery\":\"nothing matches\",\"version\":4}", OWNER).andExpect(jsonPath("$.action").value("NOT_FOUND"));
    postBody("{\"candidateSetToken\":\"" + token(old) + "\",\"selectedCandidateIndex\":1,\"version\":3}", OWNER)
        .andExpect(status().isConflict());
    postBody("{\"gameQuery\":\"Shared\",\"appId\":\"901002\",\"version\":4}", OWNER).andExpect(status().isBadRequest());
  }
  @Test void explicitIdsUseSameValidatorAsExistingGameAndNewsReads() throws Exception {
    for (String id : new String[]{"0", "-1", "4294967296", "12345678901234567890", "bad"})
      postBody("{\"appId\":\"" + id + "\",\"version\":1}", OWNER).andExpect(status().isBadRequest());
    postBody("{\"appId\":\"901099\",\"version\":1}", OWNER).andExpect(status().isNotFound());
    mvc.perform(get("/api/games/901099/news")).andExpect(status().isNotFound());
    mvc.perform(get("/api/games/901099")).andExpect(status().isNotFound());
    postBody("{\"appId\":\"901002\",\"version\":1}", OWNER).andExpect(status().isOk());
  }
  @Test void invalidQueriesAndSqlWildcardsCannotExpandIntoAllGames() throws Exception {
    postBody("{\"gameQuery\":\"   \",\"version\":1}", OWNER).andExpect(status().isBadRequest());
    postBody("{\"gameQuery\":\"" + "a".repeat(256) + "\",\"version\":1}", OWNER).andExpect(status().isBadRequest());
    assertEquals(1, resolver.resolve("%_!").candidates().size());
    assertFalse(resolver.resolve("%_!").autoSelect());
  }
  @Test void prefixesAndPartialMatchesRequireConfirmationEvenForSingleResult() {
    assertFalse(resolver.resolve("Demo 20x").autoSelect());
    assertFalse(resolver.resolve("Literal %").autoSelect());
    assertEquals("PREFIX", resolver.resolve("Literal %").candidates().getFirst().match());
    assertEquals("PARTIAL", resolver.resolve("teral").candidates().getFirst().match());
  }
  @Test void serverIssuesHttpOnlyCookieWithoutLogin() throws Exception {
    mvc.perform(post("/api/games/resolve").contentType("application/json").content("{\"gameQuery\":\"데모 2\",\"version\":1}"))
        .andExpect(status().isOk()).andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("HttpOnly")));
  }
  String token(MvcResult response) throws Exception {
    return com.jayway.jsonpath.JsonPath.read(response.getResponse().getContentAsString(), "$.candidateSetToken");
  }
}
