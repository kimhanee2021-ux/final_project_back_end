package org.ict.final_project_back_end.domain.aiCoach.dto;

import java.util.List;

// answer: 코치의 답변 문장, games: 답변에서 추천한 내 보유 게임 (최대 3개, 없으면 빈 목록)
public record CoachChatResponse(String answer, List<GameCard> games) {

  public record GameCard(Long appId, String name, String thumbnailUrl, String reason) {
  }
}
