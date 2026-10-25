package org.ict.final_project_back_end.domain.gamenews;

import org.ict.final_project_back_end.domain.game.entity.Game;
import org.ict.final_project_back_end.domain.game.repository.GameRepository;
import org.ict.final_project_back_end.domain.gamenews.client.BbsTranscriptionClient;
import org.ict.final_project_back_end.domain.gamenews.exception.BbsVoiceException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.bbs.voice.daily-calls=1000", "app.bbs.voice.calls-per-minute=1000"})
@AutoConfigureMockMvc
@Transactional
class BbsVoiceIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired GameRepository games;
  @MockitoBean BbsTranscriptionClient client;
  static final String ENDPOINT = "/api/games/bbs/voice/transcriptions";
  byte[] webm() { return new byte[]{0x1a,0x45,(byte)0xdf,(byte)0xa3,0,0,0,0,0,0,0,0}; }
  MockMultipartFile audio(String mime, byte[] bytes) { return new MockMultipartFile("audio", "arbitrary-file", mime, bytes); }
  @BeforeEach void setup() { when(client.configured()).thenReturn(true); when(client.transcribe(any(), any())).thenReturn("Counter-Strike 2"); }

  @Test void publicTranscriptionDoesNotChangeGamesOrNeedAdditionalDatabaseTables() throws Exception {
    games.saveAndFlush(Game.builder().appId(4200091001L).name("Keep").aboutTheGame("PRESERVE").build());
    long count = games.count();
    mvc.perform(multipart(ENDPOINT).file(audio("audio/webm;codecs=opus", webm())))
        .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.transcript").value("Counter-Strike 2"));
    verify(client).transcribe(any(), eq("audio/webm"));
    assertEquals(count, games.count());
    assertEquals("PRESERVE", games.findById(4200091001L).orElseThrow().getAboutTheGame());
  }
  @Test void rejectsMissingEmptyOversizedWrongTypeAndSpoofedAudioBeforeProvider() throws Exception {
    mvc.perform(multipart(ENDPOINT)).andExpect(status().isBadRequest());
    for (var file : new MockMultipartFile[]{audio("audio/webm", new byte[0]), audio("audio/webm", new byte[4_000_001]),
        audio("text/plain", webm()), audio("audio/webm", new byte[12]), audio("audio/mp4", webm()), audio("audio/wav", webm())})
      mvc.perform(multipart(ENDPOINT).file(file)).andExpect(status().isBadRequest());
    verifyNoInteractions(client);
  }
  @Test void acceptsMp4AndWavSignatures() throws Exception {
    byte[] mp4 = new byte[12]; System.arraycopy("ftyp".getBytes(), 0, mp4, 4, 4);
    byte[] wav = "RIFF0000WAVE".getBytes();
    mvc.perform(multipart(ENDPOINT).file(audio("audio/mp4", mp4))).andExpect(status().isOk());
    mvc.perform(multipart(ENDPOINT).file(audio("audio/wav", wav))).andExpect(status().isOk());
  }
  @Test void unconfiguredProviderIsReportedWithoutAttemptingTranscription() throws Exception {
    when(client.configured()).thenReturn(false);
    mvc.perform(multipart(ENDPOINT).file(audio("audio/webm", webm())))
        .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("BBS_STT_NOT_CONFIGURED"));
    verify(client, never()).transcribe(any(), any());
  }
  @Test void providerRateLimitUsesExistingErrorResponseFormat() throws Exception {
    when(client.transcribe(any(), any())).thenThrow(BbsVoiceException.limited());
    mvc.perform(multipart(ENDPOINT).file(audio("audio/webm", webm())))
        .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("BBS_STT_LIMIT"));
  }
}
