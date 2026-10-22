package org.ict.final_project_back_end.domain.aicoach.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.ict.final_project_back_end.domain.aicoach.exception.AiCoachException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// Gemini 분석 응답 중 끝까지 생성되고 형식이 맞는 답만 받아들이는지 검증한다 (Gemini 호출 없이)
class GeminiInsightClientTest {

  @Test
  void readsAnInsightOnlyFromAFinishedWellFormedResponse() throws Exception {
    ObjectMapper json = new ObjectMapper();
    String ok = "{\"verdict\":\" 판단 \",\"reason\":\"이유\",\"comparison\":\"\",\"action\":\"행동\","
        + "\"recommendations\":[{\"appId\":\"7\",\"reason\":\"그냥\"}]}";
    CoachInsightAiClient.Insight parsed = GeminiInsightClient.parseInsight(json, geminiBody("STOP", ok));
    assertEquals("판단", parsed.verdict());
    assertEquals("", parsed.comparison());
    assertEquals(7L, parsed.recommendations().get(0).appId());

    assertThrows(AiCoachException.class, () -> GeminiInsightClient.parseInsight(json, geminiBody("MAX_TOKENS", ok)));
    assertThrows(AiCoachException.class, () -> GeminiInsightClient.parseInsight(json, geminiBody("STOP", "JSON이 아님")));
    // 판단·이유·행동이 비면 버린다
    assertThrows(AiCoachException.class, () -> GeminiInsightClient.parseInsight(json,
        geminiBody("STOP", "{\"verdict\":\"\",\"reason\":\"r\",\"comparison\":\"\",\"action\":\"a\",\"recommendations\":[]}")));
    assertThrows(AiCoachException.class, () -> GeminiInsightClient.parseInsight(json,
        geminiBody("STOP", "{\"verdict\":\"v\",\"reason\":\"r\",\"comparison\":\"\",\"action\":\"\",\"recommendations\":[]}")));
    assertThrows(AiCoachException.class, () -> GeminiInsightClient.parseInsight(json,
        geminiBody("STOP", "{\"verdict\":\"" + "가".repeat(401) + "\",\"reason\":\"r\",\"comparison\":\"\",\"action\":\"a\",\"recommendations\":[]}")));
    // 추천이 네 개거나 appId가 숫자가 아니면 버린다
    String four = "[" + "{\"appId\":\"1\",\"reason\":\"r\"},".repeat(3) + "{\"appId\":\"1\",\"reason\":\"r\"}]";
    assertThrows(AiCoachException.class, () -> GeminiInsightClient.parseInsight(json,
        geminiBody("STOP", "{\"verdict\":\"v\",\"reason\":\"r\",\"comparison\":\"\",\"action\":\"a\",\"recommendations\":" + four + "}")));
    assertThrows(AiCoachException.class, () -> GeminiInsightClient.parseInsight(json,
        geminiBody("STOP", "{\"verdict\":\"v\",\"reason\":\"r\",\"comparison\":\"\",\"action\":\"a\",\"recommendations\":[{\"appId\":\"게임\",\"reason\":\"r\"}]}")));
  }

  private static byte[] geminiBody(String finishReason, String text) throws Exception {
    ObjectMapper json = new ObjectMapper();
    return json.writeValueAsBytes(Map.of("candidates", List.of(Map.of("finishReason", finishReason,
        "content", Map.of("parts", List.of(Map.of("text", text)))))));
  }
}
