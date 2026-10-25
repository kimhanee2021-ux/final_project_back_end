package org.ict.final_project_back_end.domain.aicoach.client;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// AI가 권하는 보유 게임(recommendations) 칸의 응답 스키마. 코치 채팅과 AI 분석이 같이 쓴다.
// appId를 facts.ownedGames의 번호 중 하나로만 고르게(enum) 해서, 긴 번호를 옮겨 적다 틀리는 일
// (Arma 3의 107410을 7410으로 적어 답 전체가 버려진 사례)을 막는다. Gemini는 스키마대로만 글자를 만들어 목록 밖 번호를 쓸 수 없다.
// (Groq는 strict를 끄고 보내 enum을 참고만 하므로, 서비스의 보유 게임 확인은 그대로 둔다)
final class RecommendationSchema {

  private RecommendationSchema() {
  }

  static Map<String, Object> of(Map<String, Object> facts, int maxItems) {
    Map<String, Object> appId = new LinkedHashMap<>();
    appId.put("type", "string");
    if (facts != null && facts.get("ownedGames") instanceof Map<?, ?> owned && !owned.isEmpty()) {
      appId.put("enum", owned.keySet().stream().map(String::valueOf).toList());
    }
    return Map.of("type", "array", "maxItems", maxItems,
        "items", Map.of("type", "object",
            "properties", Map.of("appId", appId, "reason", Map.of("type", "string")),
            "required", List.of("appId", "reason")));
  }
}
