package org.ict.final_project_back_end.domain.aicoach.client;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RecommendationSchemaTest {

  @SuppressWarnings("unchecked")
  private static Map<String, Object> appIdOf(Map<String, Object> responseSchema) {
    Map<String, Object> properties = (Map<String, Object>) responseSchema.get("properties");
    Map<String, Object> recommendations = (Map<String, Object>) properties.get("recommendations");
    Map<String, Object> items = (Map<String, Object>) recommendations.get("items");
    return (Map<String, Object>) ((Map<String, Object>) items.get("properties")).get("appId");
  }

  // AI가 긴 번호를 옮겨 적다 틀리지 않게(107410 -> 7410) 보유 게임 번호만 고를 수 있어야 한다
  @Test
  void appIdIsLimitedToOwnedGamesInBothCoachFeatures() {
    Map<String, Object> owned = new LinkedHashMap<>();
    owned.put("107410", Map.of("name", "Arma 3"));
    owned.put("730", Map.of("name", "Counter-Strike 2"));
    Map<String, Object> facts = Map.of("ownedGames", owned);

    assertEquals(List.of("107410", "730"), appIdOf(CoachInsightClient.responseSchema(facts)).get("enum"));
    assertEquals(List.of("107410", "730"), appIdOf(CoachReplyClient.responseSchema(facts)).get("enum"));
  }

  @Test
  void noOwnedGamesLeavesAppIdUnrestricted() {
    Map<String, Object> appId = appIdOf(CoachInsightClient.responseSchema(Map.of()));
    assertEquals("string", appId.get("type"));
    assertFalse(appId.containsKey("enum"));
  }
}
