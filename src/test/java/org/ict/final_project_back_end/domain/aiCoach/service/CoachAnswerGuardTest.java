package org.ict.final_project_back_end.domain.aiCoach.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

// AI 답변 속 금액이 서버 계산값과 같은지 검사한다 (실제로 15,500원을 15,400원으로 옮겨 적은 경우가 있었음)
class CoachAnswerGuardTest {

  private final Map<String, Object> facts = Map.of(
      "totals", Map.of("totalSpentKrw", 454023, "hourlyCostKrw", 101L),
      "bestValueGames", List.of(Map.of("name", "Isaac", "amountKrw", 15500, "playHours", 154.2)),
      "thisMonth", Map.of("budgetKrw", 300000));

  @Test
  void passesWhenEveryAmountComesFromFacts() {
    assertTrue(CoachAnswerGuard.unknownAmounts(
        "Isaac은 15,500원으로 시간당 101원이에요. 총 454,023원, 예산 300000원, 이번 달은 0원 썼어요.", facts).isEmpty());
  }

  @Test
  void catchesMiscopiedAmounts() {
    assertEquals(Set.of(15400L), CoachAnswerGuard.unknownAmounts("15,400원에 154.2시간 플레이했어요.", facts));
  }

  @Test
  void ignoresNonMoneyNumbersAndRoundedManWon() {
    // 시간·개수·연도·"3만 원" 같은 어림 표현은 금액 검사 대상이 아니다
    assertTrue(CoachAnswerGuard.unknownAmounts("2026년에 게임 3개, 154.2시간, 약 3만 원 정도예요.", facts).isEmpty());
  }
}
