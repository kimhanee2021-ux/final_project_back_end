package org.ict.final_project_back_end.domain.aiPc.service;
import com.fasterxml.jackson.databind.JsonNode;
import org.ict.final_project_back_end.domain.aiCommon.exception.AiException;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;

/** AI의 최종 판단도 확인된 근거보다 높은 등급을 확정할 수 없다. */
public final class PcAiAssessment {
 private PcAiAssessment(){}
 private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(PcAiAssessment.class);
 public static String grade(JsonNode answer,PcRules.Comparison evidence){
  String grade=answer.path("grade").asText();
  if(!grade.equals("O")&&!grade.equals("X")){log.warn("PC grade rejected: invalid enum");throw invalid();}
  if(grade.equals("O")&&!evidence.grade().equals("O")){log.warn("PC grade rejected: exceeds rule evidence");throw invalid();}
  for(String component:java.util.List.of("CPU","GPU")){
   boolean unknown=evidence.checks().stream().anyMatch(check->check.startsWith(component+":")&&check.contains("근거 없음"));
   if(unknown){String aliases=component.equals("CPU")?"CPU|프로세서":"GPU|그래픽 카드";
    var unsupported=java.util.regex.Pattern.compile("(?i)(?:"+aliases+")(?:(?:/|·|와 | 및 )(?:RAM|저장공간|저장 공간))*\\s*(?:은|는|이|가)?\\s*(?:최소|권장|요구)?\\s*(?:사양|조건)?\\s*(?:을|를)?\\s*(?:충족(?:합니다|해요|하나|하고|하므로|함|입니다|이며|이나)|충분(?:합니다|해요|하고|하므로|함)|상회(?:합니다|해요|하고|하므로|함))");
    for(String field:java.util.List.of("summary","fhd","qhd","uhd","limitation"))if(unsupported.matcher(answer.path(field).asText()).find()){log.warn("PC grade rejected: unsupported component={}, field={}",component,field);throw invalid();}
   }
  }
  return grade;
 }
 private static AiException invalid(){return new AiException(BAD_GATEWAY,"AI 평가 등급의 근거를 확인하지 못했어요.");}
}

