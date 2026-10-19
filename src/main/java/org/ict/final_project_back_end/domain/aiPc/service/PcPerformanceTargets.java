package org.ict.final_project_back_end.domain.aiPc.service;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;import java.util.Set;
import org.ict.final_project_back_end.domain.aiPc.dto.*;
/** 제한된 구간/이유 코드만 받아 숫자 실측을 만들어 내지 않는다. */
public final class PcPerformanceTargets {
 private PcPerformanceTargets(){}
 private static final Map<String,String> LABELS=Map.of("A","매우 쾌적","B","쾌적","C","보통 · 옵션 조절 권장","D","개선 권장","E","개선 필요");
 public static PcTarget unknown(String code,String reason){return new PcTarget("UNKNOWN","추정 어려움","낮음 옵션","NONE",code,reason,"AI_TARGET_BAND_V1");}
 public static PcTarget parse(JsonNode answer,String resolution,PcSpecs pc){
  if(pc.gpuModel()==null||pc.gpuModel().isBlank())return unknown("UNKNOWN_GPU","사진에서 그래픽카드 모델을 확인하지 못했어요. GPU 이름을 확인해 주세요.");
  if(pc.cpuModel()==null||pc.cpuModel().isBlank()||pc.ramGB()==null)return unknown("MISSING_REQUIREMENTS","CPU 모델과 RAM 용량을 확인해야 추정할 수 있어요.");
  var node=answer.path("targets").path(resolution);String grade=node.path("grade").asText(),reason=node.path("reason").asText().trim(),code=node.path("reasonCode").asText();
  if(reason.isEmpty()||reason.length()>240||reason.matches("(?is).*\\d+\\s*(fps|프레임).*"))return unknown("NO_COMPARISON","이 조건의 성능을 비교할 충분한 설명 근거를 확보하지 못했어요.");
  if(!LABELS.containsKey(grade)){
   if(!Set.of("UNKNOWN_GPU","NO_COMPARISON","RESOLUTION_GAP","MISSING_REQUIREMENTS","CONFLICTING_DATA").contains(code))code="NO_COMPARISON";
   return unknown(code,reason);
  }
  if(!"ESTIMATE".equals(code))return unknown("NO_COMPARISON","성능 추정의 근거와 결과가 일치하지 않아 판단을 보류했어요.");
  return new PcTarget(grade,LABELS.get(grade),"낮음 옵션","LOW","ESTIMATE",reason,"AI_TARGET_BAND_V1");
 }
}
