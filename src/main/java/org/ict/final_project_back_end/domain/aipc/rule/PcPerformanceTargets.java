package org.ict.final_project_back_end.domain.aipc.rule;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.Set;
import org.ict.final_project_back_end.domain.aipc.dto.*;

/** 제한된 구간/이유 코드만 받아 숫자 실측을 만들어 내지 않는다. */
public final class PcPerformanceTargets {
  private PcPerformanceTargets() {}

  private static final Map<String, String> LABELS =
      Map.of("A", "매우 쾌적", "B", "쾌적", "C", "보통 · 옵션 조절 권장", "D", "개선 권장", "E", "개선 필요");

  public static PcTarget unknown(String code, String reason) {
    return new PcTarget("UNKNOWN", "추정 어려움", "낮음 옵션", "NONE", code, reason, "AI_TARGET_BAND_V1");
  }

  public static PcTarget parse(
      JsonNode answer, String resolution, PcSpecs pc, PcGameRequirements requirements) {
    if ((requirements.minimum() == null || requirements.minimum().isBlank())
        && (requirements.recommended() == null || requirements.recommended().isBlank()))
      return unknown("MISSING_REQUIREMENTS", "공식 최소·권장 사양을 확인하지 못해 추정을 보류했어요.");
    return parse(answer, resolution, pc);
  }

  public static PcTarget parse(JsonNode answer, String resolution, PcSpecs pc) {
    if (pc.gpuModel() == null || pc.gpuModel().isBlank())
      return unknown("UNKNOWN_GPU", "사진에서 그래픽카드 모델을 확인하지 못했어요. GPU 이름을 확인해 주세요.");
    if (PcHardwarePolicy.ambiguousGpu(pc.gpuModel()))
      return unknown("UNKNOWN_GPU", "내장·외장 GPU가 함께 입력됐어요. 게임 분석에 사용할 GPU 하나를 선택해 주세요.");
    if ("UNIDENTIFIED".equals(PcHardwarePolicy.gpuIdentity(pc)))
      return unknown("UNKNOWN_GPU", "GPU 제품군 이름만으로 모델을 구분할 수 없어요. 정확한 GPU 모델명을 확인해 주세요.");
    if (pc.cpuModel() == null || pc.cpuModel().isBlank() || pc.ramGB() == null)
      return unknown("MISSING_REQUIREMENTS", "CPU 모델과 RAM 용량을 확인해야 추정할 수 있어요.");
    var targets = answer.path("targets");
    var node = targets.path(resolution);
    if (targets.isArray()) {
      var matches = new java.util.ArrayList<JsonNode>();
      for (var item : targets)
        if (resolution.equals(item.path("resolution").asText())) matches.add(item);
      if (matches.size() != 1) return unknown("NO_COMPARISON", "해상도별 추정 결과가 누락되거나 중복됐어요.");
      node = matches.getFirst();
    }
    String grade = node.path("grade").asText(),
        reason = node.path("reason").asText().trim(),
        code = node.path("reasonCode").asText();
    if (reason.isEmpty() || reason.length() > 240 || reason.matches("(?is).*\\d+\\s*(fps|프레임).*"))
      return unknown("NO_COMPARISON", "이 조건의 성능을 비교할 충분한 설명 근거를 확보하지 못했어요.");
    if (!LABELS.containsKey(grade)) {
      if (!Set.of(
              "UNKNOWN_GPU",
              "NO_COMPARISON",
              "RESOLUTION_GAP",
              "MISSING_REQUIREMENTS",
              "CONFLICTING_DATA")
          .contains(code)) code = "NO_COMPARISON";
      if ("UNKNOWN_GPU".equals(code)
          && "IDENTIFIED_SINGLE".equals(PcHardwarePolicy.gpuIdentity(pc)))
        return unknown("NO_COMPARISON", "GPU 모델은 확인됐지만 이 게임·해상도의 성능 구간을 판단할 비교 근거가 부족해요.");
      return unknown(code, reason);
    }
    if (!"ESTIMATE".equals(code))
      return unknown("NO_COMPARISON", "성능 추정의 근거와 결과가 일치하지 않아 판단을 보류했어요.");
    var lowerResolutions =
        "uhd".equals(resolution)
            ? java.util.List.of("fhd", "qhd")
            : "qhd".equals(resolution) ? java.util.List.of("fhd") : java.util.List.<String>of();
    for (String lower : lowerResolutions) {
      var previous = targets.path(lower);
      if (targets.isArray())
        for (var item : targets)
          if (lower.equals(item.path("resolution").asText())) previous = item;
      String previousGrade = previous.path("grade").asText();
      if (LABELS.containsKey(previousGrade) && grade.compareTo(previousGrade) < 0)
        return unknown(
            "CONFLICTING_DATA", "동일한 낮음·네이티브 조건에서 더 높은 해상도의 등급이 좋아져 결과가 충돌했어요. 다시 분석이 필요해요.");
    }
    if (PcHardwarePolicy.laptop(pc)) reason += " 노트북 전력·냉각 조건은 미확인이에요.";
    return new PcTarget(
        grade, LABELS.get(grade), "낮음 옵션", "LOW", "ESTIMATE", reason, "AI_TARGET_BAND_V1");
  }
}
