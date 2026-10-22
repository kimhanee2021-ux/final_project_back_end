package org.ict.final_project_back_end.domain.aiCoach.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

// answer: 코치의 답변 문장 (숫자는 서버가 facts 값으로 채운 것), games: 답변에서 추천한 내 보유 게임 (최대 3개, 없으면 빈 목록)
// evidence: 답변에 쓰인 숫자의 근거 (예: "2025년(작년) 지출" = "87,765원"), 화면의 "근거 보기"에 표시
public record CoachChatResponse(String answer, List<GameCard> games, List<Evidence> evidence) {

  public record GameCard(Long appId, String name, String thumbnailUrl, String reason) {
  }

  // amount/unit/group: 화면이 같은 종류의 숫자끼리 막대로 비교해 그릴 수 있게 주는 값 (원·시간·분·%만, 연도·날짜는 null)
  // group이 같고 unit이 같은 근거끼리 한 그래프에 그린다
  public record Evidence(
      @Schema(description = "근거 이름 (예: 2025년(작년) 지출)") String label,
      @Schema(description = "화면에 보여줄 값 (예: 87,765원)") String value,
      @Schema(description = "막대 그래프용 숫자. 원·시간·분·%가 아니면 null", nullable = true) Double amount,
      @Schema(description = "amount의 단위: 원, 시간, 분, %", nullable = true) String unit,
      @Schema(description = "같은 값이면 한 그래프에 묶어 비교하는 묶음 이름", nullable = true) String group) {
    public Evidence(String label, String value) {
      this(label, value, null, null, null);
    }
  }
}
