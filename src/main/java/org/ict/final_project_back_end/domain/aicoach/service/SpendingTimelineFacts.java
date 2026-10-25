package org.ict.final_project_back_end.domain.aicoach.service;

import org.ict.final_project_back_end.domain.analysis.dto.SpendingAnalysisResponse;
import org.ict.final_project_back_end.domain.analysis.dto.SpendingGameResponse;
import org.ict.final_project_back_end.domain.budget.dto.BudgetResponse;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.ict.final_project_back_end.domain.aicoach.service.FactRows.net;
import static org.ict.final_project_back_end.domain.aicoach.service.FactRows.round1;
import static org.ict.final_project_back_end.domain.aicoach.service.FactRows.row;

// 시간 흐름에 따른 지출 facts: 연도별 · 최근 12개월 · 비교(작년 대비, 최근 3개월 대비) · 최댓값 · 이번 달과 예산.
final class SpendingTimelineFacts {

  private SpendingTimelineFacts() {
  }

  // relative: AI가 "작년"을 엉뚱한 해로 읽지 않게 올해(진행 중)/작년/N년 전을 함께 적는다
  private static String relativeYear(int year, int thisYear) {
    int diff = thisYear - year;
    return diff == 0 ? "올해(진행 중)" : diff == 1 ? "작년" : diff + "년 전";
  }

  static Map<String, Map<String, Object>> byYear(List<SpendingAnalysisResponse.PurchaseView> transactions, int thisYear) {
    Map<Integer, int[]> years = new TreeMap<>(Comparator.reverseOrder());
    for (SpendingAnalysisResponse.PurchaseView t : transactions) {
      int[] sums = years.computeIfAbsent(t.purchasedAt().getYear(), y -> new int[2]);
      sums[0] += t.amountKrw();
      if (t.amountKrw() > 0) {
        sums[1]++;
      }
    }
    Map<String, Map<String, Object>> result = new LinkedHashMap<>();
    years.forEach((year, sums) -> result.put(String.valueOf(year), row("year", year, "relative", relativeYear(year, thisYear),
        "spentKrw", Math.max(0, sums[0]), "purchases", sums[1])));
    return result;
  }

  static Map<String, Map<String, Object>> byMonth(List<SpendingAnalysisResponse.PurchaseView> transactions, LocalDate today) {
    LocalDate first = today.withDayOfMonth(1).minusMonths(11);
    Map<String, Integer> months = new TreeMap<>();
    for (LocalDate m = first; !m.isAfter(today); m = m.plusMonths(1)) {
      months.put(m.toString().substring(0, 7), 0);
    }
    for (SpendingAnalysisResponse.PurchaseView t : transactions) {
      months.computeIfPresent(t.purchasedAt().toString().substring(0, 7), (k, v) -> v + t.amountKrw());
    }
    Map<String, Map<String, Object>> result = new LinkedHashMap<>();
    months.forEach((month, spent) -> result.put(month, row("spentKrw", Math.max(0, spent))));
    return result;
  }

  // "가장 많이 쓴 해/달", "가장 비싼 게임" 같은 최댓값. 연도·달은 spendingByYear의 키라 AI가 {{...}}로 가리킬 수 없어서
  // 값(year, month)으로 미리 꺼내 둔다 (예: {{highlights.mostSpentYear.year}} -> "2025년")
  static Map<String, Object> highlights(SpendingAnalysisResponse spending, List<SpendingGameResponse> paid,
                                        List<SpendingGameResponse> games, LocalDate today) {
    Map<String, Object> result = new LinkedHashMap<>();
    if (spending.hasPurchaseHistory() && !spending.transactions().isEmpty()) {
      byYear(spending.transactions(), today.getYear()).entrySet().stream()
          .max(Comparator.comparingInt(e -> (int) e.getValue().get("spentKrw")))
          .ifPresent(e -> result.put("mostSpentYear", row("year", Integer.valueOf(e.getKey()),
              "spentKrw", e.getValue().get("spentKrw"), "purchases", e.getValue().get("purchases"))));
      Map<String, Integer> months = new TreeMap<>();
      for (SpendingAnalysisResponse.PurchaseView t : spending.transactions()) {
        months.merge(t.purchasedAt().toString().substring(0, 7), t.amountKrw(), Integer::sum);
      }
      months.entrySet().stream().max(Map.Entry.comparingByValue())
          .ifPresent(e -> result.put("mostSpentMonth", row("month", e.getKey(), "spentKrw", Math.max(0, e.getValue()))));
    }
    paid.stream().max(Comparator.comparingInt(FactRows::net))
        .ifPresent(g -> result.put("mostExpensiveGame", row("appId", g.appId(), "name", g.name(), "amountKrw", net(g))));
    games.stream().filter(g -> g.hours() > 0).max(Comparator.comparingDouble(SpendingGameResponse::hours))
        .ifPresent(g -> result.put("mostPlayedGame", row("appId", g.appId(), "name", g.name(), "playHours", round1(g.hours()))));
    return result;
  }

  // AI가 직접 빼거나 나누지 않도록 자주 묻는 비교를 미리 계산한다 (차이는 절댓값 + direction)
  static Map<String, Object> comparisons(Map<String, Map<String, Object>> years,
                                         Map<String, Map<String, Object>> months, int thisYear) {
    Map<String, Object> result = new LinkedHashMap<>();
    Map<String, Object> current = years.get(String.valueOf(thisYear));
    Map<String, Object> previous = years.get(String.valueOf(thisYear - 1));
    if (current != null && previous != null) {
      int now = (int) current.get("spentKrw");
      int before = (int) previous.get("spentKrw");
      result.put("thisYearVsLastYear", row(
          "thisYear", thisYear, "lastYear", thisYear - 1,
          "thisYearSpentKrw", now, "lastYearSpentKrw", before,
          "changeKrw", Math.abs(now - before),
          "direction", now < before ? "감소" : now > before ? "증가" : "변화 없음",
          "changePercent", before == 0 ? null : Math.round(Math.abs(now - before) * 100.0 / before),
          "note", "올해는 아직 진행 중이라 연간 합계가 아님"));
    }
    int last12 = months.values().stream().mapToInt(m -> (int) m.get("spentKrw")).sum();
    result.put("last12Months", row("spentKrw", last12, "averagePerMonthKrw", Math.round(last12 / 12.0)));
    // 최근 3개월 vs 그 전 3개월 (추세). 한 달 평균이 아니라 3개월 합계끼리 비교한다
    List<Integer> monthly = months.values().stream().map(m -> (int) m.get("spentKrw")).toList();
    if (monthly.size() >= 6) {
      int recentSum = monthly.subList(monthly.size() - 3, monthly.size()).stream().mapToInt(Integer::intValue).sum();
      int earlierSum = monthly.subList(monthly.size() - 6, monthly.size() - 3).stream().mapToInt(Integer::intValue).sum();
      result.put("recent3VsPrevious3Months", row("recentSpentKrw", recentSum, "previousSpentKrw", earlierSum,
          "changeKrw", Math.abs(recentSum - earlierSum),
          "direction", recentSum < earlierSum ? "감소" : recentSum > earlierSum ? "증가" : "변화 없음",
          "changePercent", earlierSum == 0 ? null : Math.round(Math.abs(recentSum - earlierSum) * 100.0 / earlierSum)));
    }
    return result;
  }

  static Map<String, Object> thisMonth(SpendingAnalysisResponse spending, BudgetResponse budget, LocalDate today) {
    String month = today.toString().substring(0, 7);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("month", month);
    Integer spent = null;
    if (spending.hasPurchaseHistory()) {
      spent = Math.max(0, spending.transactions().stream()
          .filter(t -> t.purchasedAt().toString().startsWith(month))
          .mapToInt(SpendingAnalysisResponse.PurchaseView::amountKrw).sum());
    }
    // 값이 없는 숫자 칸은 넣지 않는다. 빈 칸이 있으면 AI가 {{thisMonth.budgetKrw}}처럼 가리켜서 숫자 확인에 실패하고
    // 다시 묻다가 결국 결과를 버린다 (예산 안 정한 회원의 예산·환불 분석이 매번 실패하던 원인)
    if (spent != null) {
      result.put("spentKrw", spent);
    } else {
      result.put("note", "구매내역을 가져오지 않아 이번 달 실제 지출을 알 수 없음");
    }
    Integer limit = budget == null ? null : budget.limitAmount();
    if (limit != null) {
      result.put("budgetKrw", limit);
      if (spent != null) {
        result.put("remainingBudgetKrw", limit - spent);
      }
    } else {
      result.put("budget", "이번 달 예산을 아직 정하지 않음 (예산·남은 예산 숫자는 없음)");
    }
    return result;
  }
}
