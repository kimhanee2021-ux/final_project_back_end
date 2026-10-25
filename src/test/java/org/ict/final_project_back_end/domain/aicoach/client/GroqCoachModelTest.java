package org.ict.final_project_back_end.domain.aicoach.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.ict.final_project_back_end.domain.aicoach.exception.AiCoachException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// 네트워크 없이 Groq(OpenAI 호환) 요청 본문과 응답 해석만 검증한다
class GroqCoachModelTest {

  private final ObjectMapper json = new ObjectMapper();

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
