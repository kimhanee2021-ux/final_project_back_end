package org.ict.final_project_back_end.domain.aiWorldcup;
import org.ict.final_project_back_end.domain.aiCommon.exception.AiException;
import org.ict.final_project_back_end.domain.aiCommon.service.AiRateLimit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WorldcupRateLimitTest {
 @Test void worldcupBudgetDoesNotConsumeExistingAiBudget(){var limit=new AiRateLimit(); for(int i=0;i<6;i++){limit.beginWorldcup(1L);limit.end(1L);} assertDoesNotThrow(()->limit.begin(1L));assertThrows(AiException.class,()->limit.beginWorldcup(1L));limit.end(1L);}
}
