package org.ict.final_project_back_end.domain.aicoach.client;

import org.ict.final_project_back_end.domain.aicoach.service.InsightTopic;
import java.util.List;
import java.util.Map;

// 분석 주제 하나에 대한 AI 결과(판단·이유·비교·다음 행동)를 만드는 AI 모델. 실제 구현은 CoachInsightClient(Gemini·Groq)이고, 테스트에서는 가짜로 바꿔 끼운다.
public interface CoachInsightAiClient {

  // 네 문장의 숫자는 {{경로}} 자리표시자라서 서비스가 실제 값으로 바꾼다. comparison은 비교할 값이 없으면 빈 문자열이다.
  // recommendations의 appId는 facts.ownedGames 안의 게임만 와야 한다 (서비스가 다시 검증한다)
  record Insight(String verdict, String reason, String comparison, String action,
                 List<CoachAiClient.Recommendation> recommendations) {
  }

  // facts: CoachFactsBuilder가 계산한 숫자
  Insight insight(Map<String, Object> facts, InsightTopic topic);
}
