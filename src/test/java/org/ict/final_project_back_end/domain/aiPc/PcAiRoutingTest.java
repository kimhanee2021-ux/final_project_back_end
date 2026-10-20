package org.ict.final_project_back_end.domain.aiPc;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.ict.final_project_back_end.domain.aiPc.service.PcAiRouting;
import org.junit.jupiter.api.Test;

class PcAiRoutingTest {
  @Test
  void smallTextUsesGroq() {
    assertTrue(PcAiRouting.preferGroq("짧은 설명", List.of(Map.of("text", "자료")), Map.of(), 900));
  }

  @Test
  void imageAndBatchAlwaysUseGemini() {
    assertFalse(PcAiRouting.preferGroq("", List.of(Map.of("inlineData", Map.of())), Map.of(), 768));
    assertFalse(
        PcAiRouting.preferGroq(
            "",
            List.of(Map.of("text", "{}")),
            Map.of("properties", Map.of("results", Map.of())),
            900));
  }

  @Test
  void countsInstructionsSchemaAndOutputBudget() {
    assertFalse(
        PcAiRouting.preferGroq("한".repeat(5000), List.of(Map.of("text", "x")), Map.of(), 900));
    assertFalse(
        PcAiRouting.preferGroq(
            "", List.of(Map.of("text", "x")), Map.of("description", "한".repeat(5000)), 900));
    assertFalse(PcAiRouting.preferGroq("", List.of(Map.of("text", "x")), Map.of(), 4500));
  }
}
