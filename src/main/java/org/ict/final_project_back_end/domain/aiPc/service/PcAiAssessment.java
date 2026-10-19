package org.ict.final_project_back_end.domain.aiPc.service;
import com.fasterxml.jackson.databind.JsonNode;
import org.ict.final_project_back_end.domain.aiCommon.exception.AiException;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;

/** AI의 최종 판단도 확인된 근거보다 높은 등급을 확정할 수 없다. */
public final class PcAiAssessment {
 private PcAiAssessment(){}
 public static String grade(JsonNode answer,PcRules.Comparison evidence){
  String grade=answer.path("grade").asText();
  if(!grade.equals("O")&&!grade.equals("X"))throw invalid();
  if(grade.equals("O")&&!evidence.grade().equals("O"))throw invalid();
  return grade;
 }
 private static AiException invalid(){return new AiException(BAD_GATEWAY,"AI 평가 등급의 근거를 확인하지 못했어요.");}
}
