package org.ict.final_project_back_end.domain.aiPc;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.ict.final_project_back_end.domain.aiPc.service.*;
import org.ict.final_project_back_end.domain.aiCommon.exception.AiException;
class PcAiAssessmentTest {
 private final ObjectMapper json=new ObjectMapper();
 @Test void acceptsConservativeAiJudgment()throws Exception{assertEquals("X",PcAiAssessment.grade(json.readTree("{\"grade\":\"X\"}"),new PcRules.Comparison("O",List.of())));}
 @Test void rejectsPassingGradeWithoutVerifiedEvidence()throws Exception{assertThrows(AiException.class,()->PcAiAssessment.grade(json.readTree("{\"grade\":\"O\"}"),new PcRules.Comparison("X",List.of())));}
 @Test void rejectsUnmeasuredLetterGradeAndMissingJudgment()throws Exception{for(String input:List.of("{}","{\"grade\":\"A\"}"))assertThrows(AiException.class,()->PcAiAssessment.grade(json.readTree(input),new PcRules.Comparison("O",List.of())));}
}
