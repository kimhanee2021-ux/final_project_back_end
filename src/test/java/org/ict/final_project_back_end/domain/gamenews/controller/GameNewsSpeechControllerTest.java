package org.ict.final_project_back_end.domain.gamenews.controller;

import org.ict.final_project_back_end.domain.gamenews.client.OpenAiNewsSpeechClient;
import org.ict.final_project_back_end.domain.gamenews.service.NewsSpeechService;
import org.ict.final_project_back_end.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GameNewsSpeechControllerTest {
  @Test void audioAndMissingKeyHaveSeparateTypedResponses() throws Exception {
    var wav = new byte[48];
    var mvc = MockMvcBuilders.standaloneSetup(new GameNewsSpeechController(new NewsSpeechService(text -> wav)))
        .setControllerAdvice(new GlobalExceptionHandler()).build();
    mvc.perform(post("/api/games/news/speech").contentType("application/json").content("{\"text\":\"도타 투\"}"))
        .andExpect(status().isOk()).andExpect(content().contentType("audio/wav"))
        .andExpect(header().string("Cache-Control", "no-store")).andExpect(content().bytes(wav));
    mvc.perform(post("/api/games/news/speech").contentType("application/json").content("{\"text\":\" \"}"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_SPEECH"));
    var unavailable = MockMvcBuilders.standaloneSetup(new GameNewsSpeechController(new NewsSpeechService(
        new OpenAiNewsSpeechClient("", "gpt-realtime-2.1-mini", "marin"))))
        .setControllerAdvice(new GlobalExceptionHandler()).build();
    unavailable.perform(post("/api/games/news/speech").contentType("application/json").content("{\"text\":\"도타 투\"}"))
        .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("SPEECH_NOT_CONFIGURED"));
  }
}
