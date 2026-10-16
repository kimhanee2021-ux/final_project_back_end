package org.ict.final_project_back_end.domain.coach.service;

import org.ict.final_project_back_end.domain.analysis.dto.SpendingAnalysisResponse;
import org.ict.final_project_back_end.domain.analysis.dto.SpendingAnalysisResponse.PurchaseView;
import org.ict.final_project_back_end.domain.analysis.dto.SpendingGameResponse;
import org.ict.final_project_back_end.domain.budget.dto.BudgetResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// 스프링 없이 코치에게 줄 숫자 계산만 검증한다 (소비 패턴 화면과 같은 규칙인지)
class CoachFactsBuilderTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 16);

  private static SpendingGameResponse game(long appId, String name, String genre, Integer amount, String source,
                                           double hours, LocalDate purchasedAt, int refund, int saved) {
    return new SpendingGameResponse(appId, name, genre, genre == null ? List.of() : List.of(genre), null, amount, source,
        null, null, hours, 0, null, null, refund, purchasedAt, 0, saved, false, null, null);
  }

  private static SpendingAnalysisResponse history(List<SpendingGameResponse> games, List<PurchaseView> transactions) {
    return new SpendingAnalysisResponse(TODAY, "PURCHASE_HISTORY", games, true, 3000, 5000, 0, 0, transactions);
  }

  @SuppressWarnings("unchecked")
  private static <T> T get(Map<String, Object> facts, String key) {
    return (T) facts.get(key);
  }

  private final List<SpendingGameResponse> games = List.of(
      game(1, "Dead Cells", "Action", 20000, "CONFIRMED", 40, LocalDate.of(2025, 3, 1), 0, 10000),
      game(2, "Ori 2", "Adventure", 5000, "CONFIRMED", 0, LocalDate.of(2026, 10, 10), 0, 4000),
      game(3, "Witcher 3", "RPG", 30000, "CONFIRMED", 2, LocalDate.of(2026, 1, 5), 10000, 0),
      game(4, "Aimlabs", "Casual", 0, "FREE", 5, null, 0, 0),
      game(5, "Gift Game", null, null, "NO_RECORD", 3, null, 0, 0));

  @Test
  void totalsUseNetAmountsAndAddUnallocatedSpending() {
    Map<String, Object> facts = CoachFactsBuilder.build(history(games, List.of()), new BudgetResponse("2026-10", null), TODAY);
    Map<String, Object> totals = get(facts, "totals");
    // 20000 + 5000 + (30000 - 환불 10000) = 45000, 나눌 수 없는 지출 3000 추가
    assertEquals(48000, totals.get("totalSpentKrw"));
    assertEquals(3, totals.get("paidGames"));
    assertEquals(1L, totals.get("freeGames"));
    assertEquals(1L, totals.get("noPaymentRecordGames"));
    // 유료 게임 금액 45000 / 유료 게임 시간 42
    assertEquals(Math.round(45000 / 42.0), totals.get("hourlyCostKrw"));
    assertEquals(5000, totals.get("savedByDiscountKrw"));
  }

  @Test
  void unplayedAndValueRankingFollowTheOneHourRule() {
    Map<String, Object> facts = CoachFactsBuilder.build(history(games, List.of()), null, TODAY);
    List<Map<String, Object>> unplayed = get(facts, "unplayedPaidGames");
    assertEquals(List.of("Ori 2"), unplayed.stream().map(g -> g.get("name")).toList());
    List<Map<String, Object>> best = get(facts, "bestValueGames");
    // 1시간 이상 유료 게임만: Dead Cells 500원/시간, Witcher 3 10000원/시간 (Ori 2는 0시간이라 제외)
    assertEquals(List.of("Dead Cells", "Witcher 3"), best.stream().map(g -> g.get("name")).toList());
    assertEquals(500L, best.get(0).get("hourlyCostKrw"));
  }

  @Test
  void refundableNeedsFourteenDaysAndUnderTwoHours() {
    Map<String, Object> facts = CoachFactsBuilder.build(history(games, List.of()), null, TODAY);
    List<Map<String, Object>> refundable = get(facts, "refundableGames");
    assertEquals(1, refundable.size());
    assertEquals("Ori 2", refundable.get(0).get("name"));
    assertEquals("2026-10-24", refundable.get(0).get("refundDeadline"));
  }

  @Test
  void thisMonthComparesSpendingWithBudget() {
    List<PurchaseView> transactions = List.of(
        new PurchaseView(LocalDate.of(2026, 10, 10), "PURCHASE", 5000, 2L, List.of("Ori 2"), 4000),
        new PurchaseView(LocalDate.of(2026, 10, 12), "REFUND", -1000, null, List.of("X"), 0),
        new PurchaseView(LocalDate.of(2025, 3, 1), "PURCHASE", 20000, 1L, List.of("Dead Cells"), 10000));
    Map<String, Object> facts = CoachFactsBuilder.build(history(games, transactions), new BudgetResponse("2026-10", 30000), TODAY);
    Map<String, Object> month = get(facts, "thisMonth");
    assertEquals(4000, month.get("spentKrw"));
    assertEquals(26000, month.get("remainingBudgetKrw"));
    List<Map<String, Object>> years = get(facts, "spendingByYear");
    assertEquals(2026, years.get(0).get("year"));
    assertEquals(4000, years.get(0).get("spentKrw"));
    assertEquals(1, years.get(0).get("purchases"));
    List<Map<String, Object>> months = get(facts, "spendingByMonthLast12");
    assertEquals(12, months.size());
    assertEquals("2026-10", months.get(11).get("month"));
  }

  @Test
  void estimatedDataHasNoMonthlySpendingOrRefunds() {
    SpendingAnalysisResponse estimated = new SpendingAnalysisResponse(TODAY, "KRW_LIST_PRICE", games, false, 0, 0, 0, 0, List.of());
    Map<String, Object> facts = CoachFactsBuilder.build(estimated, new BudgetResponse("2026-10", 30000), TODAY);
    assertFalse(facts.containsKey("spendingByYear"));
    assertFalse(facts.containsKey("refundableGames"));
    Map<String, Object> month = get(facts, "thisMonth");
    assertNull(month.get("spentKrw"));
    assertNull(month.get("remainingBudgetKrw"));
    assertTrue(((String) facts.get("amountBasis")).contains("정가"));
  }

  @Test
  void genreSpendingUsesKoreanLabelsAndOnlyPaidGames() {
    Map<String, Object> facts = CoachFactsBuilder.build(history(games, List.of()), null, TODAY);
    List<Map<String, Object>> genres = get(facts, "spendingByGenre");
    // 액션 20000, RPG 20000(환불 반영), 어드벤처 5000 / 무료·구매 기록 없는 게임은 빠짐
    assertEquals(List.of("액션", "RPG", "어드벤처"), genres.stream().map(g -> g.get("genre")).toList());
    assertEquals(44L, genres.get(0).get("percent"));
  }
}
