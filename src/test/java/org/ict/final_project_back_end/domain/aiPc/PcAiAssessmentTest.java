package org.ict.final_project_back_end.domain.aiPc;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.ict.final_project_back_end.domain.aiCommon.exception.AiException;
import org.ict.final_project_back_end.domain.aiPc.service.*;
import org.junit.jupiter.api.Test;

class PcAiAssessmentTest {
  private final ObjectMapper json = new ObjectMapper();

  @Test
  void acceptsConservativeAiJudgment() throws Exception {
    assertEquals(
        "X",
        PcAiAssessment.grade(
            json.readTree("{\"grade\":\"X\"}"), new PcRules.Comparison("O", List.of())));
  }

  @Test
  void rejectsPassingGradeWithoutVerifiedEvidence() throws Exception {
    assertThrows(
        AiException.class,
        () ->
            PcAiAssessment.grade(
                json.readTree("{\"grade\":\"O\"}"), new PcRules.Comparison("X", List.of())));
  }

  @Test
  void rejectsUnmeasuredLetterGradeAndMissingJudgment() throws Exception {
    for (String input : List.of("{}", "{\"grade\":\"A\"}"))
      assertThrows(
          AiException.class,
          () -> PcAiAssessment.grade(json.readTree(input), new PcRules.Comparison("O", List.of())));
  }

  @Test
  void rejectsObservedUnsupportedCpuRamClaim() throws Exception {
    assertThrows(
        AiException.class,
        () ->
            PcAiAssessment.grade(
                json.readTree("{\"grade\":\"X\",\"summary\":\"CPU/RAM은 충족하나 GPU 비교 불가\"}"),
                new PcRules.Comparison("X", List.of("CPU: 직접 일치하는 비교 근거 없음"))));
  }

  @Test
  void allowsExplicitUnknownCpuAndVerifiedRam() throws Exception {
    assertEquals(
        "X",
        PcAiAssessment.grade(
            json.readTree(
                "{\"grade\":\"X\",\"summary\":\"CPU는 비교 근거가 없어 충족 여부 확인이 필요하고 RAM 용량은 충족합니다.\"}"),
            new PcRules.Comparison("X", List.of("CPU: 직접 일치하는 비교 근거 없음"))));
  }

  @Test
  void allowsConditionalAndNegativeStatements() throws Exception {
    for (String text : List.of("CPU는 최소 사양을 충족하는지 확인이 필요해요", "CPU가 요구 사양을 충족하지 않습니다")) {
      var answer = json.createObjectNode().put("grade", "X").put("summary", text);
      assertEquals(
          "X",
          PcAiAssessment.grade(
              answer, new PcRules.Comparison("X", List.of("CPU: 직접 일치하는 비교 근거 없음"))));
    }
  }

  @Test
  void rejectsGroupedStorageAndRamClaim() throws Exception {
    var answer =
        json.createObjectNode().put("grade", "X").put("summary", "CPU/RAM/저장공간은 충족이나 GPU는 미확인");
    assertThrows(
        AiException.class,
        () ->
            PcAiAssessment.grade(
                answer, new PcRules.Comparison("X", List.of("CPU: 직접 일치하는 비교 근거 없음"))));
  }
}
