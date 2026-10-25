package org.ict.final_project_back_end.domain.aicoach.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.ict.final_project_back_end.domain.aicoach.dto.CoachChatRequest;
import org.ict.final_project_back_end.domain.aicoach.exception.AiCoachException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// 네트워크 없이 코치 요청 내용과 답 형식 검사만 검증한다 (공급자 호출은 GeminiCoachModelTest, GroqCoachModelTest)
class CoachReplyClientTest {

  private final ObjectMapper json = new ObjectMapper();

  @Test
  void parsesAnswerAndRecommendations() {
    CoachAiClient.Reply reply = CoachReplyClient.parseReply(json,
        "{\"answer\":\"이번 달은 4,000원 썼어요.\",\"recommendations\":[{\"appId\":\"2\",\"reason\":\"0시간이에요\"}]}");
    assertEquals("이번 달은 4,000원 썼어요.", reply.answer());
    assertEquals(List.of(new CoachAiClient.Recommendation(2L, "0시간이에요")), reply.recommendations());
  }

  @Test
  void rejectsMalformedAnswers() {
    assertThrows(AiCoachException.class, () -> CoachReplyClient.parseReply(json, "JSON이 아님"));
    assertThrows(AiCoachException.class, () -> CoachReplyClient.parseReply(json, "{\"answer\":\"\",\"recommendations\":[]}"));
    assertThrows(AiCoachException.class, () -> CoachReplyClient.parseReply(json,
        "{\"answer\":\"a\",\"recommendations\":[{\"appId\":\"게임\",\"reason\":\"r\"}]}"));
    String four = "[" + "{\"appId\":\"1\",\"reason\":\"r\"},".repeat(3) + "{\"appId\":\"1\",\"reason\":\"r\"}]";
    assertThrows(AiCoachException.class, () -> CoachReplyClient.parseReply(json, "{\"answer\":\"a\",\"recommendations\":" + four + "}"));
  }

  @Test
  void missingKeysMeanNotConfigured() {
    CoachModelRouter router = new CoachModelRouter(List.of(
        new GeminiCoachModel("", "gemini-3.5-flash-lite", "http://localhost"),
        new GroqCoachModel("", "qwen/qwen3.8-27b", "http://localhost")));
    CoachReplyClient client = new CoachReplyClient(router, "gemini,groq");
    AiCoachException e = assertThrows(AiCoachException.class, () -> client.reply(Map.of(), List.of(), "질문"));
    assertEquals("AI_NOT_CONFIGURED", e.getCode());
  }

  @Test
  void requestKeepsFactsAndQuestionInOneJsonMessageAfterHistory() throws Exception {
    CoachReplyClient client = new CoachReplyClient(new CoachModelRouter(List.of()), "gemini");
    CoachModel.Request request = client.request(Map.of("totals", Map.of("totalSpentKrw", 1000)),
        List.of(new CoachChatRequest.Turn("user", "안녕"), new CoachChatRequest.Turn("model", "안녕하세요")), "얼마 썼어?");
    assertEquals(3, request.messages().size());
    assertEquals("model", request.messages().get(1).role());
    String last = request.messages().get(2).text();
    assertTrue(last.contains("totalSpentKrw"));
    assertTrue(last.contains("얼마 썼어?"));
    assertEquals(CoachReplyClient.SYSTEM_PROMPT, request.systemPrompt());
    assertTrue(request.schema().containsKey("properties"));
  }
}
