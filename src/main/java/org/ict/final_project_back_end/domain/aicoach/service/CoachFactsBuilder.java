package org.ict.final_project_back_end.domain.aicoach.service;

import org.ict.final_project_back_end.domain.analysis.dto.SpendingAnalysisResponse;
import org.ict.final_project_back_end.domain.analysis.dto.SpendingGameResponse;
import org.ict.final_project_back_end.domain.budget.dto.BudgetResponse;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// AI 소비 코치에게 줄 "사실" 묶음을 만든다. 합계·비율·순위는 모두 여기서 계산하고,
// AI는 이 숫자를 고르고 설명만 한다 (AI가 직접 더하거나 나누다 틀리지 않게).
// 칸별 계산은 성격에 따라 SpendingPatternFacts(합계·패턴), SpendingTimelineFacts(기간별), GameListFacts(게임 목록)가 맡고,
// 이 클래스는 어떤 칸을 어떤 순서로 넣을지만 정한다. 금액·표현 규칙은 FactRows에 있다.
public final class CoachFactsBuilder {

  private CoachFactsBuilder() {
  }

  public static Map<String, Object> build(SpendingAnalysisResponse spending, BudgetResponse budget, LocalDate today) {
    List<SpendingGameResponse> games = spending.games();
    List<SpendingGameResponse> paid = games.stream().filter(g -> FactRows.net(g) > 0).toList();
    boolean history = spending.hasPurchaseHistory();

    Map<String, Object> facts = new LinkedHashMap<>();
    facts.put("today", today.toString());
    facts.put("amountBasis", history
        ? "실제 결제액 (스팀 구매내역, 해외 통화는 구매일 환율로 원화 환산)"
        : "현재 한국 스팀 정가로 추정한 금액 (구매내역을 아직 가져오지 않음, 실제 결제액·구매일 없음)");
    facts.put("totals", SpendingPatternFacts.totals(spending, games, paid));
    if (history) {
      Map<String, Map<String, Object>> years = SpendingTimelineFacts.byYear(spending.transactions(), today.getYear());
      facts.put("spendingByYear", years);
      Map<String, Map<String, Object>> months = SpendingTimelineFacts.byMonth(spending.transactions(), today);
      facts.put("spendingByMonthLast12", months);
      facts.put("comparisons", SpendingTimelineFacts.comparisons(years, months, today.getYear()));
    }
    facts.put("thisMonth", SpendingTimelineFacts.thisMonth(spending, budget, today));
    facts.put("highlights", SpendingTimelineFacts.highlights(spending, paid, games, today));
    facts.put("patterns", SpendingPatternFacts.patterns(spending, paid, games));
    facts.put("spendingByGenre", SpendingPatternFacts.byGenre(paid));
    if (history) {
      facts.put("discountHabit", SpendingPatternFacts.discountHabit(paid));
    }
    facts.put("unplayedPaidGames", GameListFacts.unplayed(paid));
    facts.put("bestValueGames", GameListFacts.valueRanking(paid, true));
    facts.put("worstValueGames", GameListFacts.valueRanking(paid, false));
    if (history) {
      facts.put("refundableGames", GameListFacts.refundable(paid, today));
    }
    facts.put("playingRecently", GameListFacts.recent(games));
    facts.put("ownedGames", GameListFacts.ownedGames(games));
    return withoutEmpty(facts);
  }

  // 값이 없는(null) 칸은 AI에게 보여주지 않는다. 빈 칸이 보이면 AI가 {{ownedGames.606150.amountKrw}}처럼 가리켜
  // 숫자 확인에 실패하고, 다시 묻다가 결과를 버린다 (금액 없는 무료 게임, 예산 안 정한 회원에서 실제로 난 실패)
  @SuppressWarnings("unchecked")
  static <T> T withoutEmpty(T value) {
    if (value instanceof Map<?, ?> map) {
      Map<Object, Object> result = new LinkedHashMap<>();
      map.forEach((key, item) -> {
        if (item != null) {
          result.put(key, withoutEmpty(item));
        }
      });
      return (T) result;
    }
    if (value instanceof List<?> list) {
      return (T) list.stream().filter(Objects::nonNull).map(CoachFactsBuilder::withoutEmpty).toList();
    }
    return value;
  }
}
