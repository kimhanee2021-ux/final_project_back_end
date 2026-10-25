package org.ict.final_project_back_end.domain.aicoach.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.ict.final_project_back_end.domain.aicoach.exception.AiCoachException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// 네트워크 없이 Gemini 요청 본문과 응답 봉투 해석만 검증한다
class GeminiCoachModelTest {

  private final ObjectMapper json = new ObjectMapper();

  private byte[] geminiBody(String finishReason, List<Map<String, Object>> parts) throws Exception {
    return json.writeValueAsBytes(Map.of("candidates", List.of(Map.of(
        "finishReason", finishReason, "content", Map.of("parts", parts)))));
  }

  @Test
  void readsOnlyFinishedAnswerTextWithoutThoughts() throws Exception {
    byte[] body = geminiBody("STOP", List.of(Map.of("text", "생각", "thought", true), Map.of("text", "{\"answer\":\"a\"}")));
    assertEquals("{\"answer\":\"a\"}", GeminiCoachModel.answerText(json, body));
    assertThrows(AiCoachException.class,
        () -> GeminiCoachModel.answerText(json, geminiBody("MAX_TOKENS", List.of(Map.of("text", "{\"answer\":\"잘림\"}")))));
  }

  @Test
  void onlyServerSideOverloadIsBusy() {
    for (int status : new int[]{500, 502, 503, 504}) {
      assertTrue(CoachModel.isServerBusy(status), "busy " + status);
    }
    for (int status : new int[]{200, 400, 401, 403, 404, 429}) {
      assertFalse(CoachModel.isServerBusy(status), "not busy " + status);
    }
  }

  @Test
  void missingKeyMeansNotConfigured() {
    GeminiCoachModel model = new GeminiCoachModel("", "gemini-3.5-flash-lite", "http://localhost");
    assertFalse(model.configured());
    AiCoachException e = assertThrows(AiCoachException.class,
        () -> model.generateJson(new CoachModel.Request("규칙", List.of(), Map.of(), 0.1, 100)));
    assertEquals("AI_NOT_CONFIGURED", e.getCode());
  }

  @Test
  void requestUsesGeminiShapeWithSchema() throws Exception {
    GeminiCoachModel model = new GeminiCoachModel("key", "gemini-3.5-flash-lite", "http://localhost");
    CoachModel.Request request = new CoachModel.Request("규칙",
        List.of(new CoachModel.Message("user", "안녕"), new CoachModel.Message("model", "안녕하세요")),
        Map.of("type", "object"), 0.1, 2048);
    Map<String, Object> body = model.requestBody(request);
    String text = new String(json.writeValueAsBytes(body), StandardCharsets.UTF_8);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> contents = (List<Map<String, Object>>) body.get("contents");
    assertEquals(2, contents.size());
    assertEquals("model", contents.get(1).get("role"));
    assertTrue(text.contains("systemInstruction"));
    assertTrue(text.contains("responseJsonSchema"));
  }
}
