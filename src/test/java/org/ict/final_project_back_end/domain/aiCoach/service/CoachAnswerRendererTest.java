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
    facts.put("patterns", Map.of(
        "profile", Map.of("name", "몰입형", "meaning", "마음에 든 한 게임에 시간을 깊게 쏟는 편"),
        "unplayed", Map.of("games", 6, "spentKrw", 38000, "percentOfPaidGames", 18L),
        "playConcentration", Map.of("topGameName", "PUBG", "topGamePercent", 42L)));
    facts.put("highlights", Map.of(
        "mostSpentYear", Map.of("year", 2024, "spentKrw", 139998),
        "mostSpentMonth", Map.of("month", "2025-03", "spentKrw", 23000)));
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
        new CoachChatResponse.Evidence("2025년(작년) 지출", "87,765원", 87765.0, "원", "spendingByYear.*.spentKrw"),
        new CoachChatResponse.Evidence("2026년(올해, 진행 중) 지출", "11,117원", 11117.0, "원", "spendingByYear.*.spentKrw"),
        new CoachChatResponse.Evidence("Isaac 플레이 시간", "154.2시간", 154.2, "시간", "bestValueGames.*.playHours"),
        new CoachChatResponse.Evidence("Isaac 시간당 비용", "101원", 101.0, "원", "bestValueGames.*.hourlyCostKrw")), r.evidence());
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
  void rendersPeakYearAndMonthWithUnits() {
    CoachAnswerRenderer.Rendered r = CoachAnswerRenderer.render(
        "{{highlights.mostSpentYear.year}}에 {{highlights.mostSpentYear.spentKrw}}으로 가장 많이 썼고, "
            + "달로는 {{highlights.mostSpentMonth.month}}이에요.", facts, "가장 돈을 많이 쓴 년도는 언제야");
    assertTrue(r.ok(), r.problems().toString());
    assertEquals("2024년에 139,998원으로 가장 많이 썼고, 달로는 2025년 3월이에요.", r.text());
    assertEquals(List.of(
        new CoachChatResponse.Evidence("가장 많이 쓴 해 연도", "2024년"),
        new CoachChatResponse.Evidence("가장 많이 쓴 해 지출", "139,998원", 139998.0, "원", "highlights.mostSpentYear"),
        new CoachChatResponse.Evidence("가장 많이 쓴 달 월", "2025년 3월")), r.evidence());
  }

  @Test
  void rendersAStructuredAnalysisWithBulletsAndKeepsTextValuesOutOfEvidence() {
    CoachAnswerRenderer.Rendered r = CoachAnswerRenderer.render(
        "{{patterns.profile.name}}이에요. {{patterns.profile.meaning}}.\n"
            + "• {{patterns.playConcentration.topGameName}}이(가) 전체 플레이의 {{patterns.playConcentration.topGamePercent}}를 차지해요.\n"
            + "• 안 한 게임 {{patterns.unplayed.games}}개에 {{patterns.unplayed.spentKrw}}가 묶여 있어요 "
            + "(유료 게임의 {{patterns.unplayed.percentOfPaidGames}}).", facts, "내 소비 패턴 분석해 줘");
    assertTrue(r.ok(), r.problems().toString());
    assertTrue(r.text().startsWith("몰입형이에요. 마음에 든 한 게임에 시간을 깊게 쏟는 편."), r.text());
    assertTrue(r.text().contains("\n• PUBG"), r.text());
    assertTrue(r.text().contains("42%"), r.text());
    assertTrue(r.text().contains("6개에 38,000원이"), r.text());
    // 이름·설명 같은 글자 값은 근거 목록에서 빠지고, 숫자 값만 남는다
    assertEquals(List.of("플레이 집중도 가장 오래 한 게임 비중", "안 한 게임 게임 수", "안 한 게임 지출", "안 한 게임 유료 게임 중 비율"),
        r.evidence().stream().map(CoachChatResponse.Evidence::label).toList());
  }

  @Test
  void fixesSubjectParticlesAndParenthesizedParticlesAfterValues() {
    // 38,000원+가 -> 이, 42%+이 -> 가, 괄호 조사는 받침에 맞는 하나만, "이번"·"가장" 같은 낱말은 건드리지 않는다
    assertEquals("38,000원이 묶여 있어요",
        CoachAnswerRenderer.render("{{patterns.unplayed.spentKrw}}가 묶여 있어요", facts, "").text());
    assertEquals("42%가 차지해요",
        CoachAnswerRenderer.render("{{patterns.playConcentration.topGamePercent}}이 차지해요", facts, "").text());
    assertEquals("38,000원이 묶여 있어요",
        CoachAnswerRenderer.render("{{patterns.unplayed.spentKrw}}이(가) 묶여 있어요", facts, "").text());
    assertEquals("42%는 높아요",
        CoachAnswerRenderer.render("{{patterns.playConcentration.topGamePercent}}은(는) 높아요", facts, "").text());
    assertEquals("38,000원 이번 달 가장 커요",
        CoachAnswerRenderer.render("{{patterns.unplayed.spentKrw}} 이번 달 가장 커요", facts, "").text());
    assertEquals("42%가장 커요",
        CoachAnswerRenderer.render("{{patterns.playConcentration.topGamePercent}}가장 커요", facts, "").text());
  }

  @Test
  void removesDoubledYearAndMonthUnitsWhenTheAiAddsThemAgain() {
    // 실제 답변: "2024년년이에요" / "2025년 3월월"
    assertEquals("2024년이에요",
        CoachAnswerRenderer.render("{{highlights.mostSpentYear.year}}년이에요", facts, "").text());
    assertEquals("2025년 3월이에요",
        CoachAnswerRenderer.render("{{highlights.mostSpentMonth.month}}월이에요", facts, "").text());
    // 실제 답변: 연도를 앞에 따로 쓰고 (연도가 포함된) 월 값을 또 붙임 -> 한 번만
    assertEquals("가장 많이 쓴 달은 2025년 3월이에요",
        CoachAnswerRenderer.render("가장 많이 쓴 달은 2025년 {{highlights.mostSpentMonth.month}}이에요", facts, "2025년").text());
  }

  @Test
  void evidenceCarriesAmountUnitAndGroupSoTheScreenCanDrawBars() {
    CoachAnswerRenderer.Rendered r = CoachAnswerRenderer.render(
        "{{patterns.unplayed.percentOfPaidGames}} {{patterns.playConcentration.topGamePercent}} {{totals.totalPlayHours}}", facts, "");
    assertEquals(List.of(
        new CoachChatResponse.Evidence("안 한 게임 유료 게임 중 비율", "18%", 18.0, "%", "patterns.unplayed"),
        new CoachChatResponse.Evidence("플레이 집중도 가장 오래 한 게임 비중", "42%", 42.0, "%", "patterns.playConcentration"),
        new CoachChatResponse.Evidence("총 플레이 시간", "541.5시간", 541.5, "시간", "totals")), r.evidence());
    assertEquals("spendingByMonthLast12.*.spentKrw", CoachAnswerRenderer.groupOf("spendingByMonthLast12.2026-03.spentKrw"));
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
  void acceptsADirectlyWrittenNumberOnlyWhenItIsExactlyAFactsValue() {
    // 실제 502: AI가 {{...playHours}} 대신 "63.4시간"을 직접 씀 -> facts에 있는 값과 똑같으면 받고 근거 보기에도 넣는다
    CoachAnswerRenderer.Rendered ok = CoachAnswerRenderer.render("Isaac은 154.2시간 플레이했어요.", facts, "");
    assertTrue(ok.ok(), ok.problems().toString());
    assertEquals(List.of(new CoachChatResponse.Evidence("Isaac 플레이 시간", "154.2시간", 154.2, "시간", "bestValueGames.*.playHours")), ok.evidence());
    // 어느 칸과도 다른 숫자는 여전히 실패 (옮겨 적다 틀린 경우)
    assertEquals(List.of("직접 쓴 숫자: 154.3시간"), CoachAnswerRenderer.render("Isaac은 154.3시간 했어요.", facts, "").problems());
  }

  @Test
  void fixesPastTenseCopulaAfterValues() {
    // 실제 답변: "87,765원였고" -> 받침 있는 값 뒤는 "이었", 없는 값 뒤는 "였"
    assertEquals("87,765원이었고, 올해는 33%였어요",
        CoachAnswerRenderer.render("{{spendingByYear.2025.spentKrw}}였고, 올해는 {{discountHabit.discountedUnplayedPercent}}이었어요", facts, "").text());
  }

  @Test
  void allowsNumbersFromTheQuestionAndRulePhrases() {
    assertTrue(CoachAnswerRenderer.render("30분만 할 수 있다면 짧은 게임이 좋아요. 1시간 이상 한 게임 기준이에요.",
        facts, "30분만 할 건데 뭐 하지?").ok());
  }
}
