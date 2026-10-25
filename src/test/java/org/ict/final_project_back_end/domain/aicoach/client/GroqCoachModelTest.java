package org.ict.final_project_back_end.domain.aicoach.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.ict.final_project_back_end.domain.aicoach.exception.AiCoachException;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// 네트워크 없이 Groq(OpenAI 호환) 요청 본문과 응답 해석만 검증한다
class GroqCoachModelTest {

  private final ObjectMapper json = new ObjectMapper();

  // 요청이 분당 토큰 한도보다 크면(413) 한도 초과(429)처럼 알려서 라우터가 다른 공급자로 넘기게 한다.
  // 예전에는 413을 형식 오류로 봐서 Gemini로 넘어가지 못하고 AI 분석 전체가 실패했다.
  @Test
  void requestTooLargeFallsBackLikeRateLimit() throws Exception {
    for (int status : new int[]{413, 429}) {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/", exchange -> {
        exchange.sendResponseHeaders(status, -1);
        exchange.close();
      });
      server.start();
      try {
        GroqCoachModel model = new GroqCoachModel("key", "qwen/qwen3.8-27b", "http://127.0.0.1:" + server.getAddress().getPort());
        AiCoachException e = assertThrows(AiCoachException.class,
            () -> model.generateJson(new CoachModel.Request("규칙", List.of(new CoachModel.Message("user", "안녕")), Map.of("type", "object"), 0.2, 100)));
        assertEquals("AI_RATE_LIMITED", e.getCode(), "HTTP " + status);
      } finally {
        server.stop(0);
      }
    }
  }

  private byte[] groqBody(String finishReason, String content) throws Exception {
    return json.writeValueAsBytes(Map.of("choices", List.of(Map.of(
        "finish_reason", finishReason, "message", Map.of("content", content)))));
  }

  @Test
  void requestPutsSystemPromptFirstAndRenamesModelRole() {
    GroqCoachModel model = new GroqCoachModel("key", "qwen/qwen3.8-27b", "http://localhost/");
    CoachModel.Request request = new CoachModel.Request("규칙",
        List.of(new CoachModel.Message("user", "안녕"), new CoachModel.Message("model", "안녕하세요")),
        Map.of("type", "object"), 0.2, 1024);
    Map<String, Object> body = model.requestBody(request);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> messages = (List<Map<String, Object>>) body.get("messages");
    assertEquals(List.of("system", "user", "assistant"), messages.stream().map(m -> m.get("role")).toList());
    assertEquals("규칙", messages.get(0).get("content"));
    assertEquals(1024, body.get("max_completion_tokens"));
    assertEquals("none", body.get("reasoning_effort"));
    assertTrue(body.get("response_format").toString().contains("json_schema"));
  }

  @Test
  void readsOnlyFinishedAnswers() throws Exception {
    assertEquals("{\"answer\":\"a\"}", GroqCoachModel.answerText(json, groqBody("stop", "{\"answer\":\"a\"}")));
    assertThrows(AiCoachException.class, () -> GroqCoachModel.answerText(json, groqBody("length", "{\"answer\":")));
  }

  @Test
  void missingKeyMeansNotConfigured() {
    assertFalse(new GroqCoachModel("", "qwen/qwen3.8-27b", "http://localhost").configured());
    assertTrue(new GroqCoachModel("key", "qwen/qwen3.8-27b", "http://localhost").configured());
  }
}
