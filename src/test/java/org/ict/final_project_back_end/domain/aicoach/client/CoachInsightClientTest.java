package org.ict.final_project_back_end.domain.aicoach.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.ict.final_project_back_end.domain.aicoach.exception.AiCoachException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// 분석 답 중 형식이 맞는 답만 받아들이는지 검증한다 (공급자 호출 없이)
class CoachInsightClientTest {

  @Test
  void readsAnInsightOnlyFromAWellFormedAnswer() {
    ObjectMapper json = new ObjectMapper();
    String ok = "{\"verdict\":\" 판단 \",\"reason\":\"이유\",\"comparison\":\"\",\"action\":\"행동\","
        + "\"recommendations\":[{\"appId\":\"7\",\"reason\":\"그냥\"}]}";
    CoachInsightAiClient.Insight parsed = CoachInsightClient.parseInsight(json, ok);
    assertEquals("판단", parsed.verdict());
    assertEquals("", parsed.comparison());
    assertEquals(7L, parsed.recommendations().get(0).appId());

    assertThrows(AiCoachException.class, () -> CoachInsightClient.parseInsight(json, "JSON이 아님"));
    // 판단·이유·행동이 비면 버린다
    assertThrows(AiCoachException.class, () -> CoachInsightClient.parseInsight(json,
        "{\"verdict\":\"\",\"reason\":\"r\",\"comparison\":\"\",\"action\":\"a\",\"recommendations\":[]}"));
    assertThrows(AiCoachException.class, () -> CoachInsightClient.parseInsight(json,
        "{\"verdict\":\"v\",\"reason\":\"r\",\"comparison\":\"\",\"action\":\"\",\"recommendations\":[]}"));
    assertThrows(AiCoachException.class, () -> CoachInsightClient.parseInsight(json,
        "{\"verdict\":\"" + "가".repeat(401) + "\",\"reason\":\"r\",\"comparison\":\"\",\"action\":\"a\",\"recommendations\":[]}"));
    // 추천이 네 개거나 appId가 숫자가 아니면 버린다
    String four = "[" + "{\"appId\":\"1\",\"reason\":\"r\"},".repeat(3) + "{\"appId\":\"1\",\"reason\":\"r\"}]";
    assertThrows(AiCoachException.class, () -> CoachInsightClient.parseInsight(json,
        "{\"verdict\":\"v\",\"reason\":\"r\",\"comparison\":\"\",\"action\":\"a\",\"recommendations\":" + four + "}"));
    assertThrows(AiCoachException.class, () -> CoachInsightClient.parseInsight(json,
        "{\"verdict\":\"v\",\"reason\":\"r\",\"comparison\":\"\",\"action\":\"a\",\"recommendations\":[{\"appId\":\"게임\",\"reason\":\"r\"}]}"));
  }
}
