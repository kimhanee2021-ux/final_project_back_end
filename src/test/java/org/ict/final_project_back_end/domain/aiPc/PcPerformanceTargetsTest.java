package org.ict.final_project_back_end.domain.aiPc;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.ict.final_project_back_end.domain.aiPc.dto.PcSpecs;
import org.ict.final_project_back_end.domain.aiPc.service.PcPerformanceTargets;
import org.junit.jupiter.api.Test;

class PcPerformanceTargetsTest {
  private PcSpecs pc(String gpu) {
    return new PcSpecs("i7-13700", "", gpu, "", 32.0, "", 1000.0, 500.0, "");
  }

  private com.fasterxml.jackson.databind.JsonNode answer(String grade, String code, String reason)
      throws Exception {
    return new ObjectMapper()
        .readTree(
            "{\"targets\":{\"fhd\":{\"grade\":\""
                + grade
                + "\",\"reasonCode\":\""
                + code
                + "\",\"reason\":\""
                + reason
                + "\"}}}");
  }

  @Test
  void returnsConditionalBandWithLowConfidence() throws Exception {
    var r =
        PcPerformanceTargets.parse(answer("B", "ESTIMATE", "낮음 옵션에서 조건부 추정"), "fhd", pc("UHD770"));
    assertEquals("B", r.grade());
    assertEquals("LOW", r.confidence());
    assertEquals("낮음 옵션", r.preset());
  }

  @Test
  void missingGpuOverridesAiGrade() throws Exception {
    assertEquals(
        "UNKNOWN_GPU",
        PcPerformanceTargets.parse(answer("A", "ESTIMATE", "추정"), "fhd", pc("")).reasonCode());
  }

  @Test
  void unknownKeepsSpecificReason() throws Exception {
    var r =
        PcPerformanceTargets.parse(
            answer("UNKNOWN", "RESOLUTION_GAP", "해상도 비교자료 부족"), "fhd", pc("UHD770"));
    assertEquals("추정 어려움", r.label());
    assertEquals("RESOLUTION_GAP", r.reasonCode());
  }

  @Test
  void missingResolutionAndInventedNumericFpsDoNotBecomeGrades() throws Exception {
    assertEquals(
        "UNKNOWN",
        PcPerformanceTargets.parse(answer("A", "ESTIMATE", "144FPS 보장"), "fhd", pc("UHD770"))
            .grade());
    assertEquals(
        "UNKNOWN",
        PcPerformanceTargets.parse(answer("A", "ESTIMATE", "추정"), "uhd", pc("UHD770")).grade());
  }

  @Test
  void flatResolutionArrayIsAcceptedWithoutBorrowingAnotherResolution() throws Exception {
    var data =
        new ObjectMapper()
            .readTree(
                "{\"targets\":[{\"resolution\":\"fhd\",\"grade\":\"C\",\"reasonCode\":\"ESTIMATE\",\"reason\":\"낮음"
                    + " 옵션에서 비교 추정\"}]}");
    assertEquals("C", PcPerformanceTargets.parse(data, "fhd", pc("UHD770")).grade());
    assertEquals("UNKNOWN", PcPerformanceTargets.parse(data, "uhd", pc("UHD770")).grade());
  }

  @Test
  void mixedGpuNamesAreUnknownEvenWhenAiReturnsA() throws Exception {
    assertEquals(
        "UNKNOWN_GPU",
        PcPerformanceTargets.parse(
                answer("A", "ESTIMATE", "추정"),
                "fhd",
                pc("RTX 5060 Laptop GPU, AMD Radeon 780M Graphics"))
            .reasonCode());
  }

  @Test
  void higherResolutionCannotImproveWithoutChangedSettings() throws Exception {
    var data =
        new ObjectMapper()
            .readTree(
                "{\"targets\":{\"fhd\":{\"grade\":\"C\",\"reasonCode\":\"ESTIMATE\",\"reason\":\"낮음"
                    + " 옵션\"},\"qhd\":{\"grade\":\"A\",\"reasonCode\":\"ESTIMATE\",\"reason\":\"낮음"
                    + " 옵션\"}}}");
    assertEquals(
        "CONFLICTING_DATA", PcPerformanceTargets.parse(data, "qhd", pc("UHD770")).reasonCode());
  }

  @Test
  void knownGpuDoesNotBecomeUnreadableJustBecausePerformanceIsUnknown() throws Exception {
    var target =
        PcPerformanceTargets.parse(
            answer("UNKNOWN", "UNKNOWN_GPU", "내장 그래픽 성능 미확인"), "fhd", pc("Intel UHD Graphics 770"));
    assertEquals("UNKNOWN", target.grade());
    assertEquals("NO_COMPARISON", target.reasonCode());
    assertTrue(target.reason().contains("GPU 모델은 확인"));
  }
}
