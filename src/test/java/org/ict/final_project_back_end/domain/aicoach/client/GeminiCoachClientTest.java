package org.ict.final_project_back_end.domain.aicoach.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.ict.final_project_back_end.domain.aicoach.dto.CoachChatRequest;
import org.ict.final_project_back_end.domain.aicoach.exception.AiCoachException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// 네트워크 없이 Gemini 요청 본문과 응답 해석만 검증한다
class GeminiCoachClientTest {

  private final ObjectMapper json = new ObjectMapper();

  private byte[] geminiBody(String finishReason, String text) throws Exception {
    return json.writeValueAsBytes(Map.of("candidates", List.of(Map.of(
        "finishReason", finishReason,
        "content", Map.of("parts", List.of(Map.of("text", text)))))));
  }

  @Test
  void parsesAnswerAndRecommendations() throws Exception {
    byte[] body = geminiBody("STOP", "{\"answer\":\"이번 달은 4,000원 썼어요.\",\"recommendations\":[{\"appId\":\"2\",\"reason\":\"0시간이에요\"}]}");
    CoachAiClient.Reply reply = GeminiCoachClient.parseReply(json, body);
    assertEquals("이번 달은 4,000원 썼어요.", reply.answer());
    assertEquals(List.of(new CoachAiClient.Recommendation(2L, "0시간이에요")), reply.recommendations());
  }

  @Test
  void rejectsIncompleteOrMalformedAnswers() throws Exception {
    assertThrows(AiCoachException.class,
        () -> GeminiCoachClient.parseReply(json, geminiBody("MAX_TOKENS", "{\"answer\":\"잘림\",\"recommendations\":[]}")));
    assertThrows(AiCoachException.class,
        () -> GeminiCoachClient.parseReply(json, geminiBody("STOP", "JSON이 아님")));
    assertThrows(AiCoachException.class,
        () -> GeminiCoachClient.parseReply(json, geminiBody("STOP", "{\"answer\":\"\",\"recommendations\":[]}")));
    assertThrows(AiCoachException.class,
        () -> GeminiCoachClient.parseReply(json, geminiBody("STOP", "{\"answer\":\"a\",\"recommendations\":[{\"appId\":\"게임\",\"reason\":\"r\"}]}")));
    String four = "[" + "{\"appId\":\"1\",\"reason\":\"r\"},".repeat(3) + "{\"appId\":\"1\",\"reason\":\"r\"}]";
    assertThrows(AiCoachException.class,
        () -> GeminiCoachClient.parseReply(json, geminiBody("STOP", "{\"answer\":\"a\",\"recommendations\":" + four + "}")));
  }

  @Test
  void onlyServerSideOverloadIsRetried() {
    for (int status : new int[]{500, 502, 503, 504}) {
      assertTrue(GeminiCoachClient.isBusy(status), "retry " + status);
    }
    for (int status : new int[]{200, 400, 401, 403, 404, 429}) {
      assertFalse(GeminiCoachClient.isBusy(status), "no retry " + status);
    }
  }

  @Test
  void missingKeyMeansNotConfigured() {
    GeminiCoachClient client = new GeminiCoachClient("", "gemini-flash-latest", "http://localhost");
    AiCoachException e = assertThrows(AiCoachException.class, () -> client.reply(Map.of(), List.of(), "질문"));
    assertEquals("AI_NOT_CONFIGURED", e.getCode());
  }

  @Test
  void requestKeepsFactsAndQuestionInOneJsonMessageAfterHistory() throws Exception {
    GeminiCoachClient client = new GeminiCoachClient("key", "gemini-flash-latest", "http://localhost");
    Map<String, Object> body = client.requestBody(Map.of("totals", Map.of("totalSpentKrw", 1000)),
        List.of(new CoachChatRequest.Turn("user", "안녕"), new CoachChatRequest.Turn("model", "안녕하세요")), "얼마 썼어?");
    String text = new String(json.writeValueAsBytes(body), StandardCharsets.UTF_8);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> contents = (List<Map<String, Object>>) body.get("contents");
    assertEquals(3, contents.size());
    assertEquals("model", contents.get(1).get("role"));
    assertTrue(text.contains("totalSpentKrw"));
    assertTrue(text.contains("얼마 썼어?"));
    assertTrue(text.contains("responseJsonSchema"));
  }
}
