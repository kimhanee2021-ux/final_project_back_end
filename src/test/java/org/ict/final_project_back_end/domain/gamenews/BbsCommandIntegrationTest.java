package org.ict.final_project_back_end.domain.gamenews;

import jakarta.persistence.EntityManager;
import java.util.*;
import org.ict.final_project_back_end.domain.game.entity.Game;
import org.ict.final_project_back_end.domain.game.repository.GameRepository;
import org.ict.final_project_back_end.domain.game.service.BbsGameSearchService;
import org.ict.final_project_back_end.domain.gamenews.client.BbsCommandClient;
import org.ict.final_project_back_end.domain.gamenews.client.BbsCommandClient.*;
import org.ict.final_project_back_end.domain.gamenews.exception.BbsCommandException;
import org.ict.final_project_back_end.domain.gamenews.service.BbsCommandService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"app.bbs.command.daily-calls=1000","app.bbs.command.calls-per-minute=1000"})
@AutoConfigureMockMvc
@Transactional
class BbsCommandIntegrationTest {
  @Autowired MockMvc mvc;@Autowired GameRepository games;@Autowired EntityManager em;@Autowired BbsGameSearchService search;
  @MockitoBean BbsCommandClient client;
  static final long APP=4200096001L;
  static final String URL="/api/games/bbs/voice/commands";
  @BeforeEach void setup() {
    games.save(Game.builder().appId(APP).name("Voice Fixture 2").aboutTheGame("KEEP EXISTING DATA").build());
    games.save(Game.builder().appId(APP+1).name("Voice Fixture Workshop").build());em.flush();
    when(client.configured()).thenReturn(true);when(client.interpret(anyString())).thenReturn(new Intent(Action.LATEST_BRIEFING,"Voice Fixture 2"));
  }
  org.springframework.test.web.servlet.ResultActions command() throws Exception { return mvc.perform(post(URL).contentType("application/json").content("{\"transcript\":\"음성 테스트 게임 최신 소식 들려줘\"}")); }
  @Test void resolvesExactTitleWithoutChangingCatalog() throws Exception {
    long count=games.count();command().andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RESOLVED"))
        .andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.game.appId").value(APP));
    em.flush();em.clear();
    assertEquals(count,games.count());assertEquals("KEEP EXISTING DATA",games.findById(APP).orElseThrow().getAboutTheGame());
  }
  @Test void ambiguousPartialNamesRequireSelection() throws Exception {
    when(client.interpret(anyString())).thenReturn(new Intent(Action.LATEST_BRIEFING,"Voice Fixture"));
    command().andExpect(jsonPath("$.status").value("CHOOSE_GAME")).andExpect(jsonPath("$.candidates.length()").value(2)).andExpect(jsonPath("$.game").doesNotExist());
  }
  @Test void missingGamesAreNotResolved() throws Exception {
    when(client.interpret(anyString())).thenReturn(new Intent(Action.LATEST_BRIEFING,"no-such-voice-game-xyz"));command().andExpect(jsonPath("$.status").value("NOT_FOUND"));
  }
  @Test void bareSeriesTitleDoesNotAutomaticallySelectOlderExactTitleWhenSequelAlsoMatches() throws Exception {
    games.save(Game.builder().appId(APP+2).name("Voice Fixture").build());em.flush();
    when(client.interpret(anyString())).thenReturn(new Intent(Action.LATEST_BRIEFING,"Voice Fixture"));
    command().andExpect(jsonPath("$.status").value("CHOOSE_GAME")).andExpect(jsonPath("$.candidates.length()").value(3));
    when(client.interpret(anyString())).thenReturn(new Intent(Action.LATEST_BRIEFING,"Voice Fixture 2"));
    command().andExpect(jsonPath("$.status").value("RESOLVED")).andExpect(jsonPath("$.game.appId").value(APP));
  }
  @Test void bareNameMissingNameAndUnsupportedIntentHaveNoAutomaticBriefing() throws Exception {
    for(var pair:List.of(new String[]{"SEARCH","Voice Fixture 2","SEARCH"},new String[]{"LATEST_BRIEFING","","NEEDS_GAME"},new String[]{"UNSUPPORTED","","UNSUPPORTED"})) {
      when(client.interpret(anyString())).thenReturn(new Intent(Action.valueOf(pair[0]),pair[1]));
      command().andExpect(jsonPath("$.status").value(pair[2])).andExpect(jsonPath("$.game").doesNotExist());
    }
  }
  @Test void rejectsBadInputBeforeModelAndRejectsUnconfiguredProvider() throws Exception {
    for(String body:List.of("{}","null","{\"transcript\":\"\"}","{\"transcript\":\""+"a".repeat(256)+"\"}","{\"transcript\":\"bad\\nrequest\"}"))
      mvc.perform(post(URL).contentType("application/json").content(body)).andExpect(status().isBadRequest());
    verify(client,never()).interpret(anyString());when(client.configured()).thenReturn(false);command().andExpect(status().isServiceUnavailable());verify(client,never()).interpret(anyString());
  }
  @Test void voiceControlsDoNotNeedAGameNameAndNeverWriteCatalog() throws Exception {
    long count=games.count();
    for(var action:List.of(Action.NEXT_BRIEFING,Action.PAUSE,Action.RESUME)) {
      when(client.interpret(anyString())).thenReturn(new Intent(action,""));
      command().andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONTROL"))
          .andExpect(jsonPath("$.action").value(action.name())).andExpect(jsonPath("$.game").doesNotExist());
    }
    assertEquals(count,games.count());
    when(client.interpret(anyString())).thenReturn(new Intent(Action.RESUME,"invented game"));
    command().andExpect(status().isBadGateway());
  }
  @Test void failedRequestsConsumeBudgetAndNeverRetry() {
    var service=new BbsCommandService(client,search,1,10,1);when(client.interpret(anyString())).thenThrow(BbsCommandException.unavailable());
    assertThrows(BbsCommandException.class,() -> service.resolve("latest news","local"));assertThrows(BbsCommandException.class,() -> service.resolve("latest news","local"));verify(client,times(1)).interpret(anyString());
  }
}
