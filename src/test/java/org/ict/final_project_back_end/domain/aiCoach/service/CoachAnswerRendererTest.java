package org.ict.final_project_back_end.domain.aiCoach.service;

import org.ict.final_project_back_end.domain.aiCoach.dto.CoachChatResponse;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// AI가 쓴 자리표시자를 서버가 실제 값으로 채우는지, 숫자를 직접 쓰거나 없는 칸을 가리키면 잡아내는지 검증한다
class CoachAnswerRendererTest {

  private final Map<String, Object> facts = new LinkedHashMap<>();

  {
    facts.put("totals", Map.of("totalSpentKrw", 454023, "paidGames", 33, "totalPlayHours", 541.5));
    Map<String, Object> years = new LinkedHashMap<>();
    years.put("2026", Map.of("relative", "올해(진행 중)", "spentKrw", 11117));
    years.put("2025", Map.of("relative", "작년", "spentKrw", 87765));
    facts.put("spendingByYear", years);
    facts.put("bestValueGames", List.of(Map.of("name", "Isaac", "amountKrw", 15500, "playHours", 154.2, "hourlyCostKrw", 101L)));
    facts.put("discountHabit", Map.of("discountedUnplayedPercent", 33L));
    facts.put("refundableGames", List.of(Map.of("name", "Ori 2", "refundDeadline", "2026-10-24")));
    facts.put("ownedGames", Map.of("250900", Map.of("name", "Isaac", "playMinutes", 48)));
  }

  @Test
  void fillsPlaceholdersWithFormattedValuesAndCollectsEvidence() {
    CoachAnswerRenderer.Rendered r = CoachAnswerRenderer.render(
        "작년에는 {{spendingByYear.2025.spentKrw}}, 올해는 {{ spendingByYear.2026.spentKrw }}를 썼어요. "
            + "{{bestValueGames.0.name}}은 {{bestValueGames.0.playHours}} 동안 시간당 {{bestValueGames.0.hourlyCostKrw}}예요.",
        facts, "작년이랑 올해 비교해 줘");
    assertTrue(r.ok(), r.problems().toString());
    // 조사도 값의 받침에 맞게 고친다: 11,117원+를 -> 을, 101원+예요 -> 이에요
    assertEquals("작년에는 87,765원, 올해는 11,117원을 썼어요. Isaac은 154.2시간 동안 시간당 101원이에요.", r.text());
    assertEquals(List.of(
        new CoachChatResponse.Evidence("2025년(작년) 지출", "87,765원"),
        new CoachChatResponse.Evidence("2026년(올해, 진행 중) 지출", "11,117원"),
        new CoachChatResponse.Evidence("Isaac 플레이 시간", "154.2시간"),
        new CoachChatResponse.Evidence("Isaac 시간당 비용", "101원")), r.evidence());
  }

  @Test
  void formatsPercentDatesMinutesCountsAndRemovesDoubledUnits() {
    CoachAnswerRenderer.Rendered r = CoachAnswerRenderer.render(
        "{{discountHabit.discountedUnplayedPercent}} · {{refundableGames.0.refundDeadline}}까지 · "
            + "{{ownedGames.250900.playMinutes}} · 유료 {{totals.paidGames}}개 · {{totals.totalSpentKrw}}원",
        facts, "");
    assertTrue(r.ok(), r.problems().toString());
    assertEquals("33% · 2026년 10월 24일까지 · 48분 · 유료 33개 · 454,023원", r.text());
  }

  @Test
  void particlesFollowTheValueEvenWhenTheAiGuessedWrong() {
    assertEquals("33%를 안 했어요, 48분은 짧아요, 541.5시간과 비교하면",
        CoachAnswerRenderer.render("{{discountHabit.discountedUnplayedPercent}}을 안 했어요, "
            + "{{ownedGames.250900.playMinutes}}는 짧아요, {{totals.totalPlayHours}}와 비교하면", facts, "").text());
    // 로/으로: 받침 있으면 으로, ㄹ 받침(일)은 로 (실제 답변의 "129원로" 사례)
    assertEquals("101원으로, 2026년 10월 24일로",
        CoachAnswerRenderer.render("{{bestValueGames.0.hourlyCostKrw}}로, {{refundableGames.0.refundDeadline}}으로", facts, "").text());
  }

  @Test
  void rejectsUnknownPathsAndNumbersWrittenDirectly() {
    assertEquals(List.of("없는 칸: spendingByYear.2023.spentKrw"),
        CoachAnswerRenderer.render("{{spendingByYear.2023.spentKrw}}", facts, "").problems());
    // 실제로 있었던 실수: 15,500원을 15,400원으로 옮겨 적음
    assertEquals(List.of("직접 쓴 숫자: 15,400원"),
        CoachAnswerRenderer.render("Isaac은 15,400원이에요.", facts, "").problems());
    // 표 전체를 가리키는 것도 안 됨
    assertFalse(CoachAnswerRenderer.render("{{totals}}", facts, "").ok());
  }

  @Test
  void allowsNumbersFromTheQuestionAndRulePhrases() {
    assertTrue(CoachAnswerRenderer.render("30분만 할 수 있다면 짧은 게임이 좋아요. 1시간 이상 한 게임 기준이에요.",
        facts, "30분만 할 건데 뭐 하지?").ok());
  }
}
