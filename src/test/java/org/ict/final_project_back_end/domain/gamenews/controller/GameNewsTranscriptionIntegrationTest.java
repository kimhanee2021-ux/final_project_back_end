package org.ict.final_project_back_end.domain.gamenews.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.ict.final_project_back_end.domain.game.entity.*;
import org.ict.final_project_back_end.domain.game.service.GameNameNormalizer;
import org.ict.final_project_back_end.domain.gamenews.client.NewsTranscriptionClient;
import org.ict.final_project_back_end.domain.gamenews.entity.GameNewsSupport;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:news-stt;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "app.game-search.sync-enabled=false", "app.seed.games.enabled=false", "app.featured.sync-on-startup=false"})
@AutoConfigureMockMvc @Transactional
class GameNewsTranscriptionIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired EntityManager em;
  @MockitoBean NewsTranscriptionClient stt;
  @Autowired org.ict.final_project_back_end.domain.game.repository.GameRepository gameRows;
  @Autowired org.ict.final_project_back_end.domain.game.repository.GameSearchNameRepository nameRows;
  @Autowired org.springframework.transaction.support.TransactionTemplate tx;
  final ObjectMapper json = new ObjectMapper();
  @BeforeEach void setup() {
    for (var entry : Map.of(570L, "Dota 2", 730L, "Counter-Strike 2").entrySet()) {
      var game = Game.builder().appId(entry.getKey()).name(entry.getValue()).build(); em.persist(game);
      em.persist(new GameSearchName(game, entry.getValue(), GameNameNormalizer.normalize(entry.getValue()), GameSearchName.Kind.CANONICAL, "test"));
      em.persist(new GameNewsSupport(game, GameNewsSupport.Status.SUPPORTED, "test"));
    }
    em.flush();
    new org.ict.final_project_back_end.domain.game.service.GameSearchCatalogSync(gameRows, nameRows, tx).synchronize();
  }
  @Test void recordedAudioTranscriptFlowsThroughActualResolverToSameAppId() throws Exception {
    for (var entry : Map.of("도타 투 업데이트 알려줘", 570, "카운터스트라이크2 업데이트 알려줘", 730,
        "Dota two updates", 570, "CounterStrike 2 updates", 730).entrySet()) {
      when(stt.transcribe(any(), eq("audio/wav"), anyList())).thenReturn(entry.getKey());
      var response = mvc.perform(multipart("/api/games/news/transcribe").file(wav()))
          .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
          .andExpect(jsonPath("$.transcript").value(entry.getKey())).andReturn().getResponse();
      String transcript = json.readTree(response.getContentAsString()).get("transcript").asText();
      mvc.perform(post("/api/games/news/voice-intent").contentType("application/json")
          .content(json.writeValueAsString(Map.of("transcript", transcript, "version", 1))))
          .andExpect(status().isOk()).andExpect(jsonPath("$.appId").value(entry.getValue()))
          .andExpect(jsonPath("$.action").value("SEARCH"));
    }
  }
  @Test void selectedGameSuppliesRelevantVocabularyAndInvalidUploadsNeverCallProvider() throws Exception {
    when(stt.transcribe(any(), anyString(), anyList())).thenReturn("패치");
    mvc.perform(multipart("/api/games/news/transcribe").file(wav()).param("appId", "730"))
        .andExpect(status().isOk());
    verify(stt).transcribe(any(), eq("audio/wav"), argThat(hints -> hints.contains("Counter-Strike 2") && hints.contains("카운터스트라이크2") && !hints.contains("Dota 2")));
    clearInvocations(stt);
    mvc.perform(multipart("/api/games/news/transcribe").file(new MockMultipartFile("audio", "bad.wav", "audio/wav", "not audio".getBytes())))
        .andExpect(status().isBadRequest());
    mvc.perform(multipart("/api/games/news/transcribe").file(new MockMultipartFile("audio", "large.wav", "audio/wav", new byte[4_000_001])))
        .andExpect(status().isBadRequest());
    mvc.perform(multipart("/api/games/news/transcribe").file(wav()).param("appId", "0"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(stt);
  }
  MockMultipartFile wav() {
    return new MockMultipartFile("audio", "sample.wav", "audio/wav", "RIFF0000WAVEfixture".getBytes(StandardCharsets.US_ASCII));
  }
}
