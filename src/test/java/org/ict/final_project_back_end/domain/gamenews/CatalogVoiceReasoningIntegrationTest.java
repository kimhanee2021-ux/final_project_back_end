package org.ict.final_project_back_end.domain.gamenews;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.ict.final_project_back_end.domain.game.entity.Game;
import org.ict.final_project_back_end.domain.game.entity.GameSearchName;
import org.ict.final_project_back_end.domain.game.repository.GameRepository;
import org.ict.final_project_back_end.domain.game.repository.GameSearchNameRepository;
import org.ict.final_project_back_end.domain.game.service.GameNameNormalizer;
import org.ict.final_project_back_end.domain.game.service.GameSearchCatalogSync;
import org.ict.final_project_back_end.domain.game.service.GameSearchException;
import org.ict.final_project_back_end.domain.gamenews.client.catalog.GameCatalogAiClient;
import org.ict.final_project_back_end.domain.gamenews.dto.NewsVoiceIntent.Context;
import org.ict.final_project_back_end.domain.gamenews.dto.NewsVoiceIntent.Request;
import org.ict.final_project_back_end.domain.gamenews.dto.NewsVoiceIntent.Slots;
import org.ict.final_project_back_end.domain.gamenews.entity.GameNewsSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:catalog-reasoning;MODE=MySQL;DB_CLOSE_DELAY=-1",
      "app.game-search.sync-enabled=false",
      "app.seed.games.enabled=false",
      "app.featured.sync-on-startup=false"
    })
@AutoConfigureMockMvc
@Transactional
class CatalogVoiceReasoningIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired EntityManager em;
  @Autowired GameRepository games;
  @Autowired GameSearchNameRepository names;
  @Autowired TransactionTemplate tx;
  @MockitoBean GameCatalogAiClient ai;

  @MockitoBean
  org.ict.final_project_back_end.domain.gamenews.client.intent.NewsVoiceIntentAiClient intent;

  final ObjectMapper json = new ObjectMapper();
  final Cookie owner = new Cookie("gs_news_voice", "12345678-1234-1234-1234-123456789012");

  @BeforeEach
  void setup() {
    add(910001, "Celestial Harbour 4", "천체 항구 4");
    add(910002, "Celestial Harbour 5", "천체 항구 5");
    add(910003, "ELDEN RING", null);
    add(910004, "Baldur's Gate 3", "발더스 게이트 3");
    when(intent.extract(anyString()))
        .thenAnswer(
            invocation -> {
              String text = invocation.getArgument(0);
              var slots =
                  org.ict.final_project_back_end.domain.gamenews.service.NewsVoiceRules.extract(
                      text);
              return new Slots(
                  slots.gameQuery(),
                  slots.category(),
                  slots.allCategories(),
                  slots.multipleCategories(),
                  slots.useCurrentGame(),
                  slots.command(),
                  List.of());
            });
    when(intent.extract(anyString(), any()))
        .thenAnswer(
            invocation -> {
              String text = invocation.getArgument(0);
              var slots =
                  org.ict.final_project_back_end.domain.gamenews.service.NewsVoiceRules.extract(
                      text);
              return new Slots(
                  slots.gameQuery(),
                  slots.category(),
                  slots.allCategories(),
                  slots.multipleCategories(),
                  slots.useCurrentGame(),
                  slots.command(),
                  List.of());
            });
    em.flush();
    new GameSearchCatalogSync(games, names, tx).synchronize();
    em.flush();
  }

  void add(long id, String name, String korean) {
    var game = Game.builder().appId(id).name(name).build();
    em.persist(game);
    if (korean != null)
      em.persist(
          new GameSearchName(
              game,
              korean,
              GameNameNormalizer.normalize(korean),
              GameSearchName.Kind.ALIAS,
              "official-localized"));
    em.persist(new GameNewsSupport(game, GameNewsSupport.Status.SUPPORTED, "test"));
  }

  ResultActions ask(String transcript, Context context, long version) throws Exception {
    return mvc.perform(
        post("/api/games/news/voice-intent")
            .cookie(owner)
            .contentType("application/json")
            .content(json.writeValueAsBytes(new Request(transcript, context, version))));
  }

  @Test
  void missingKoreanAliasExpandsToRealCatalogGameRatherThanFixedId() throws Exception {
    when(intent.extract("엘든 링 패치"))
        .thenReturn(
            new Slots("엘든 링", "PATCH", false, false, false, "SEARCH", List.of("Elden Ring")));
    when(ai.choose(anyString(), any(), anyList()))
        .thenReturn(new GameCatalogAiClient.Decision(1, false));
    ask("엘든 링 패치", null, 1)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.action").value("SEARCH"))
        .andExpect(jsonPath("$.appId").value(910003))
        .andExpect(jsonPath("$.name").value("ELDEN RING"));
    verifyNoInteractions(ai);
    verify(intent).extract("엘든 링 패치");
  }

  @Test
  void generatedAbbreviationUsesModelAndActualCandidates() throws Exception {
    when(ai.choose(anyString(), any(), anyList()))
        .thenReturn(new GameCatalogAiClient.Decision(1, false));
    ask("BG3 패치", null, 1).andExpect(status().isOk()).andExpect(jsonPath("$.appId").value(910004));
    verify(ai, never()).expand(anyString(), any());
    verify(ai).choose(anyString(), any(), anyList());
  }

  @Test
  void unifiedInterpretationMarksPhoneticGuessAsAmbiguousEvenWithOneExactExpandedTitle()
      throws Exception {
    when(intent.extract("알든 링 패치"))
        .thenReturn(
            new Slots("알든 링", "PATCH", false, false, false, "SEARCH", List.of("ELDEN RING"), true));
    ask("알든 링 패치", null, 1).andExpect(jsonPath("$.action").value("CHOOSE_GAME"));
    verifyNoInteractions(ai);
  }

  @Test
  void ambiguousSeriesCannotBeAutoSelectedEvenIfModelOverconfident() throws Exception {
    when(ai.choose(anyString(), any(), anyList()))
        .thenReturn(new GameCatalogAiClient.Decision(1, false));
    ask("CH 패치", null, 1)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.action").value("CHOOSE_GAME"))
        .andExpect(jsonPath("$.candidates.length()").value(2))
        .andExpect(jsonPath("$.appId").isEmpty());
  }

  @Test
  void modelReceivesValidatedCurrentGameAndMayResolveAbbreviationFromContext() throws Exception {
    when(ai.choose(anyString(), any(), anyList()))
        .thenReturn(new GameCatalogAiClient.Decision(2, false));
    ask("CH 패치", new Context(910002L, null, false, null, null, null), 1)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.appId").value(910002));
    verify(ai)
        .choose(
            anyString(),
            argThat(c -> c.appId() == 910002L && c.gameName().equals("Celestial Harbour 5")),
            anyList());
  }

  @Test
  void ambiguousDecisionRequiresSelectionAndKeepsCategoryAndOwnerBoundToken() throws Exception {
    when(ai.choose(anyString(), any(), anyList()))
        .thenReturn(new GameCatalogAiClient.Decision(null, true));
    var response =
        ask("CH 패치", null, 7)
            .andExpect(jsonPath("$.action").value("CHOOSE_GAME"))
            .andReturn()
            .getResponse();
    var body = json.readTree(response.getContentAsString());
    var context =
        new Context(null, "PATCH", false, "GAME", body.path("candidateSetToken").asText(), 7L);
    ask("2번", context, 8)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.appId").value(910002))
        .andExpect(jsonPath("$.category").value("PATCH"));
  }

  @Test
  void nonexistentModelNamesAndChangedSequelNumbersAreNotAccepted() throws Exception {
    when(intent.extract("천체항구3 패치"))
        .thenReturn(
            new Slots(
                "천체항구3",
                "PATCH",
                false,
                false,
                false,
                "SEARCH",
                List.of("Game not in the catalog", "Celestial Harbour 4")));
    ask("천체항구3 패치", null, 1)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.action").value("CLARIFY"))
        .andExpect(jsonPath("$.appId").isEmpty());
    verify(ai, never()).choose(anyString(), any(), anyList());
  }

  @Test
  void forgedIndexAndProviderFailureFallBackToCandidatesWithout500() throws Exception {
    when(ai.choose(anyString(), any(), anyList()))
        .thenReturn(new GameCatalogAiClient.Decision(999999, false));
    ask("BG3 패치", null, 1)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.action").value("CHOOSE_GAME"));
    when(ai.choose(anyString(), any(), anyList()))
        .thenThrow(GameSearchException.invalid("provider failed"));
    ask("BG3 패치", null, 2)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.action").value("CHOOSE_GAME"));
  }

  @Test
  void exactLocalizedNameNeedsNoModelAndNearSpellingNeedsConfirmation() throws Exception {
    ask("천체 항구 4 패치", null, 1).andExpect(jsonPath("$.appId").value(910001));
    verifyNoInteractions(ai);
    when(ai.choose(anyString(), any(), anyList()))
        .thenReturn(new GameCatalogAiClient.Decision(1, false));
    ask("천체 항구 5x 패치", null, 2).andExpect(jsonPath("$.action").value("CHOOSE_GAME"));
  }

  @Test
  void playtestOnlyCandidateDoesNotBecomeOrdinaryGameWithoutConfirmation() throws Exception {
    add(910005, "Mythic Story 3 Playtest", null);
    em.flush();
    new GameSearchCatalogSync(games, names, tx).synchronize();
    em.flush();
    when(ai.choose(anyString(), any(), anyList()))
        .thenReturn(new GameCatalogAiClient.Decision(1, false));
    ask("MS3 패치", null, 1).andExpect(jsonPath("$.action").value("CHOOSE_GAME"));
  }

  @Test
  void exactTranslatedTitleIsNotPollutedByAnAmbiguousAbbreviationVariant() throws Exception {
    when(intent.extract("엘든 링 패치"))
        .thenReturn(
            new Slots("엘든 링", "PATCH", false, false, false, "SEARCH", List.of("ELDEN RING", "CH")));
    when(ai.choose(anyString(), any(), anyList()))
        .thenReturn(new GameCatalogAiClient.Decision(1, false));
    ask("엘든 링 패치", null, 1).andExpect(jsonPath("$.appId").value(910003));
    verifyNoInteractions(ai);
  }
}
