package org.ict.final_project_back_end.domain.gamenews;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.ict.final_project_back_end.domain.game.entity.Game;
import org.ict.final_project_back_end.domain.game.entity.GameSearchName;
import org.ict.final_project_back_end.domain.game.service.GameNameNormalizer;
import org.ict.final_project_back_end.domain.game.service.GameSearchException;
import org.ict.final_project_back_end.domain.gamenews.client.NewsBriefingAiClient;
import org.ict.final_project_back_end.domain.gamenews.client.SteamNewsClient;
import org.ict.final_project_back_end.domain.gamenews.client.intent.NewsVoiceIntentAiClient;
import org.ict.final_project_back_end.domain.gamenews.dto.NewsVoiceIntent.AiContext;
import org.ict.final_project_back_end.domain.gamenews.dto.NewsVoiceIntent.Context;
import org.ict.final_project_back_end.domain.gamenews.dto.NewsVoiceIntent.Request;
import org.ict.final_project_back_end.domain.gamenews.dto.NewsVoiceIntent.Slots;
import org.ict.final_project_back_end.domain.gamenews.dto.NewsVoiceIntent.Turn;
import org.ict.final_project_back_end.domain.gamenews.entity.GameNewsSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:voice-intent;MODE=MySQL;DB_CLOSE_DELAY=-1",
      "app.game-search.sync-enabled=false",
      "app.seed.games.enabled=false",
      "app.featured.sync-on-startup=false"
    })
@AutoConfigureMockMvc
@Transactional
class NewsVoiceIntentIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired EntityManager em;
  @MockitoBean NewsVoiceIntentAiClient ai;
  @MockitoBean NewsBriefingAiClient summary;
  @MockitoBean SteamNewsClient steam;
  final ObjectMapper json = new ObjectMapper();
  Cookie owner;

  @BeforeEach
  void setup() {
    owner = new Cookie("gs_news_voice", UUID.randomUUID().toString());
    game(570, "Dota 2", GameNewsSupport.Status.SUPPORTED, "도타2");
    game(571, "Dota 2 Test", GameNewsSupport.Status.SUPPORTED, null);
    game(730, "Counter-Strike 2", GameNewsSupport.Status.SUPPORTED, "카스2");
    game(997, "Empty 2", GameNewsSupport.Status.SUPPORTED, null);
    game(998, "Unsupported 2", GameNewsSupport.Status.UNSUPPORTED, null);
    game(999, "Unknown 2", null, null);
    em.flush();
    em.clear();
  }

  void game(long id, String name, GameNewsSupport.Status status, String alias) {
    var game = Game.builder().appId(id).name(name).build();
    em.persist(game);
    em.persist(
        new GameSearchName(
            game, name, GameNameNormalizer.normalize(name), GameSearchName.Kind.CANONICAL, "test"));
    if (alias != null)
      em.persist(
          new GameSearchName(
              game, alias, GameNameNormalizer.normalize(alias), GameSearchName.Kind.ALIAS, "test"));
    if (status != null) em.persist(new GameNewsSupport(game, status, "test"));
  }

  ResultActions ask(String text, Context context, long version) throws Exception {
    return mvc.perform(
        post("/api/games/news/voice-intent")
            .cookie(owner)
            .contentType("application/json")
            .content(json.writeValueAsBytes(new Request(text, context, version))));
  }

  @AfterEach
  void noNewSummariesOrCollection() {
    verifyNoInteractions(summary, steam);
  }

  @Test
  void combinedNameCategoryAndAllNewsUseDbIdsWithoutAi() throws Exception {
    ask("도타2 패치 알려줘", null, 1)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.action").value("SEARCH"))
        .andExpect(jsonPath("$.appId").value(570))
        .andExpect(jsonPath("$.category").value("PATCH"));
    ask("도타2 전체 소식", null, 2)
        .andExpect(jsonPath("$.action").value("SEARCH"))
        .andExpect(jsonPath("$.allCategories").value(true))
        .andExpect(jsonPath("$.category").isEmpty());
    verifyNoInteractions(ai);
  }

  @Test
  void gameFirstThenCategoryAndCategoryFirstThenGamePreserveMissingSlots() throws Exception {
    ask("도타2", null, 1)
        .andExpect(jsonPath("$.awaiting").value("CATEGORY"))
        .andExpect(jsonPath("$.appId").value(570));
    ask("이벤트 알려줘", new Context(570L, null, false, "CATEGORY", null, null), 2)
        .andExpect(jsonPath("$.action").value("SEARCH"))
        .andExpect(jsonPath("$.category").value("EVENT"));
    ask("패치 알려줘", null, 3)
        .andExpect(jsonPath("$.action").value("CLARIFY"))
        .andExpect(jsonPath("$.awaiting").value("GAME"))
        .andExpect(jsonPath("$.category").value("PATCH"));
    ask("도타2", new Context(null, "PATCH", false, "GAME", null, null), 4)
        .andExpect(jsonPath("$.action").value("SEARCH"))
        .andExpect(jsonPath("$.appId").value(570))
        .andExpect(jsonPath("$.category").value("PATCH"));
  }

  @Test
  void candidateChoiceKeepsCategoryAndValidatesOwnerVersionAndId() throws Exception {
    var result =
        ask("Dota 패치 알려줘", null, 8)
            .andExpect(jsonPath("$.action").value("CHOOSE_GAME"))
            .andReturn();
    var body = json.readTree(result.getResponse().getContentAsByteArray());
    String token = body.path("candidateSetToken").asText();
    var context = new Context(null, "PATCH", false, "GAME", token, 8L);
    Cookie original = owner;
    owner = new Cookie("gs_news_voice", UUID.randomUUID().toString());
    ask("1번", context, 9).andExpect(status().isConflict());
    owner = original;
    ask("1번", new Context(null, "PATCH", false, "GAME", token, 7L), 10)
        .andExpect(status().isConflict());
    ask("1번", context, 11)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.action").value("SEARCH"))
        .andExpect(jsonPath("$.appId").value(570))
        .andExpect(jsonPath("$.category").value("PATCH"));
    ask("1번", context, 12).andExpect(status().isConflict());
  }

  @Test
  void multipleCategoriesClarifyAndNewGameDoesNotSilentlyInheritOldCategory() throws Exception {
    ask("도타2 패치랑 이벤트 알려줘", null, 1)
        .andExpect(jsonPath("$.action").value("CLARIFY"))
        .andExpect(jsonPath("$.awaiting").value("CATEGORY"))
        .andExpect(jsonPath("$.appId").value(570));
    ask("카스2", new Context(570L, "PATCH", false, null, null, null), 2)
        .andExpect(jsonPath("$.appId").value(730))
        .andExpect(jsonPath("$.awaiting").value("CATEGORY"));
  }

  @Test
  void directIdAndCurrentSelectionUseCommonValidation() throws Exception {
    ask("게임 번호 730 패치 알려줘", null, 1)
        .andExpect(jsonPath("$.action").value("SEARCH"))
        .andExpect(jsonPath("$.appId").value(730));
    ask("이 게임 이벤트 알려줘", new Context(570L, null, true, null, null, null), 2)
        .andExpect(jsonPath("$.appId").value(570))
        .andExpect(jsonPath("$.category").value("EVENT"));
    ask("게임 번호 0 패치", null, 3).andExpect(status().isBadRequest());
    ask("패치", new Context(4294967295L, null, false, null, null, null), 4)
        .andExpect(status().isNotFound());
    ask("2번", null, 5)
        .andExpect(jsonPath("$.awaiting").value("GAME"))
        .andExpect(jsonPath("$.appId").isEmpty());
  }

  @Test
  void unsupportedUnknownAndEmptyStoredBriefingRemainDistinct() throws Exception {
    ask("Unknown 2 패치", null, 1)
        .andExpect(jsonPath("$.action").value("UNSUPPORTED"))
        .andExpect(jsonPath("$.support").value("UNKNOWN"));
    ask("Unsupported 2 패치", null, 2).andExpect(jsonPath("$.support").value("UNSUPPORTED"));
    ask("Empty 2 패치", null, 3)
        .andExpect(jsonPath("$.action").value("SEARCH"))
        .andExpect(jsonPath("$.briefing").value("NO_STORED_BRIEFING"));
  }

  @Test
  void complexNaturalLanguageUsesBoundedExtractorThenCommonDbResolver() throws Exception {
    String text = "도타2에서 이번에 바뀐 내용을 정리해줘";
    when(ai.extract(text)).thenReturn(new Slots("도타2", "PATCH", false, false, false, "SEARCH"));
    ask(text, null, 1)
        .andExpect(jsonPath("$.action").value("SEARCH"))
        .andExpect(jsonPath("$.appId").value(570));
    ask(text, null, 2)
        .andExpect(jsonPath("$.action").value("SEARCH"))
        .andExpect(jsonPath("$.appId").value(570));
    verify(ai).extract(text);
  }

  @Test
  void providerFailuresAreExplicitAndNeverBecomeOtherOrInventedGames() throws Exception {
    String text = "요즘 어떤 내용이 바뀌었는지 알려줄래";
    when(ai.extract(text))
        .thenThrow(
            new GameSearchException(
                HttpStatus.SERVICE_UNAVAILABLE, "VOICE_AI_UNAVAILABLE", "의도 확인 실패"));
    ask(text, null, 1).andExpect(status().isServiceUnavailable());
  }

  @Test
  void fixedCommandsAndInvalidInputDoNotCallAi() throws Exception {
    for (String[] command :
        new String[][] {
          {"다시 읽어줘", "REPLAY"},
          {"처음부터", "RESET"},
          {"다른 게임", "CHANGE_GAME"},
          {"중지", "STOP"},
          {"오늘 날씨", "UNSUPPORTED"}
        }) ask(command[0], null, 1).andExpect(jsonPath("$.action").value(command[1]));
    ask("a".repeat(501), null, 2).andExpect(status().isBadRequest());
    ask("패치", new Context(null, "INVALID", false, null, null, null), 3)
        .andExpect(status().isBadRequest());
    verifyNoInteractions(ai);
  }

  @Test
  void correctionsReplaceCategoryOrGameButKeepUnchangedSlotsAndDbValidation() throws Exception {
    ask("도타2 패치 아니 이벤트 알려줘", null, 1)
        .andExpect(jsonPath("$.appId").value(570))
        .andExpect(jsonPath("$.category").value("EVENT"))
        .andExpect(jsonPath("$.action").value("SEARCH"));
    var selected = new Context(570L, "PATCH", false, null, null, null);
    ask("아니 이벤트 알려줘", selected, 2)
        .andExpect(jsonPath("$.appId").value(570))
        .andExpect(jsonPath("$.category").value("EVENT"));
    ask("그거 말고 카스2", selected, 3)
        .andExpect(jsonPath("$.appId").value(730))
        .andExpect(jsonPath("$.category").value("PATCH"));
    ask("대신 전체 소식", selected, 4)
        .andExpect(jsonPath("$.allCategories").value(true))
        .andExpect(jsonPath("$.category").isEmpty());
    ask("아니", selected, 5)
        .andExpect(jsonPath("$.action").value("CLARIFY"))
        .andExpect(jsonPath("$.appId").value(570))
        .andExpect(jsonPath("$.category").value("PATCH"));
    ask("아니 이벤트", new Context(4294967295L, "PATCH", false, null, null, null), 6)
        .andExpect(status().isNotFound());
    verifyNoInteractions(ai);
  }

  @Test
  void legacyJsonWithoutHistoryIsStillSupported() throws Exception {
    mvc.perform(
            post("/api/games/news/voice-intent")
                .cookie(owner)
                .contentType("application/json")
                .content("{\"transcript\":\"도타2 패치\",\"version\":1}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.appId").value(570))
        .andExpect(jsonPath("$.category").value("PATCH"));
    verifyNoInteractions(ai);
  }

  @Test
  void geminiReceivesValidatedCurrentSelectionAndBoundedHistory() throws Exception {
    String text = "그거 말고 아까 이야기한 게임의 바뀐 내용으로 알려줘";
    when(ai.extract(eq(text), any(AiContext.class)))
        .thenReturn(new Slots(null, "PATCH", false, false, true, "SEARCH"));
    var history =
        List.of(new Turn("도타2 이벤트 알려줘", "SEARCH", 570L, "tampered name", "EVENT", false, null));
    mvc.perform(
            post("/api/games/news/voice-intent")
                .cookie(owner)
                .contentType("application/json")
                .content(
                    json.writeValueAsBytes(
                        new Request(
                            text,
                            new Context(570L, "EVENT", false, null, null, null),
                            1L,
                            history))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.appId").value(570))
        .andExpect(jsonPath("$.category").value("PATCH"));
    var captured = org.mockito.ArgumentCaptor.forClass(AiContext.class);
    verify(ai).extract(eq(text), captured.capture());
    assertEquals("Dota 2", captured.getValue().gameName());
    assertEquals("EVENT", captured.getValue().category());
    assertEquals("Dota 2", captured.getValue().history().getFirst().name());
    assertEquals("도타2 이벤트 알려줘", captured.getValue().history().getFirst().transcript());
  }

  @Test
  void malformedOrUnvalidatedHistoryNeverReachesGemini() throws Exception {
    for (var history :
        List.of(
            Collections.nCopies(5, new Turn("도타2", "SEARCH", 570L, null, "PATCH", false, null)),
            List.of(new Turn("도타2", "SEARCH", 570L, null, "INVALID", false, null)))) {
      mvc.perform(
              post("/api/games/news/voice-intent")
                  .cookie(owner)
                  .contentType("application/json")
                  .content(json.writeValueAsBytes(new Request("아까 이야기한 내용", null, 1L, history))))
          .andExpect(status().isBadRequest());
    }
    mvc.perform(
            post("/api/games/news/voice-intent")
                .cookie(owner)
                .contentType("application/json")
                .content(
                    json.writeValueAsBytes(
                        new Request(
                            "아까 이야기한 내용",
                            null,
                            1L,
                            List.of(
                                new Turn(
                                    "없는 게임", "SEARCH", 4294967295L, null, null, false, null))))))
        .andExpect(status().isNotFound());
    verifyNoInteractions(ai);
  }
}
