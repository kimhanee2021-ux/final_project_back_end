package org.ict.final_project_back_end.domain.coach.service;

import org.ict.final_project_back_end.domain.coach.dto.CoachChatRequest;

import java.util.List;
import java.util.Map;

// AI 소비 코치의 답변을 만드는 AI 모델. 실제 구현은 Gemini(GeminiCoachClient)이고, 테스트에서는 가짜로 바꿔 끼운다.
public interface CoachAiClient {

  // appId: facts.ownedGames 안의 게임만 와야 한다 (서비스가 다시 검증한다)
  record Recommendation(Long appId, String reason) {
  }

  record Reply(String answer, List<Recommendation> recommendations) {
  }

  // facts: CoachFactsBuilder가 계산한 숫자, history: 앞선 대화, message: 이번 질문
  Reply reply(Map<String, Object> facts, List<CoachChatRequest.Turn> history, String message);
}
