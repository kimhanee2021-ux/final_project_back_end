package org.ict.final_project_back_end.domain.aiCoach.dto;

import java.util.List;

// answer: 코치의 답변 문장 (숫자는 서버가 facts 값으로 채운 것), games: 답변에서 추천한 내 보유 게임 (최대 3개, 없으면 빈 목록)
// evidence: 답변에 쓰인 숫자의 근거 (예: "2025년(작년) 지출" = "87,765원"), 화면의 "근거 보기"에 표시
public record CoachChatResponse(String answer, List<GameCard> games, List<Evidence> evidence) {

  public record GameCard(Long appId, String name, String thumbnailUrl, String reason) {
  }

  public record Evidence(String label, String value) {
  }
}
