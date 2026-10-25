package org.ict.final_project_back_end.domain.aicoach.service;

import org.ict.final_project_back_end.domain.analysis.dto.SpendingAnalysisResponse;
import org.ict.final_project_back_end.domain.analysis.dto.SpendingGameResponse;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.ict.final_project_back_end.domain.aicoach.service.FactRows.genre;
import static org.ict.final_project_back_end.domain.aicoach.service.FactRows.net;
import static org.ict.final_project_back_end.domain.aicoach.service.FactRows.percent;
import static org.ict.final_project_back_end.domain.aicoach.service.FactRows.round1;
import static org.ict.final_project_back_end.domain.aicoach.service.FactRows.row;

// 소비 습관 facts: 합계 · 패턴(미플레이 · 몰입도 · 가격대 · 장르 가성비 · 할인 의존도 · 소비 유형) · 장르별 지출 · 할인 구매 습관.
final class SpendingPatternFacts {

  private SpendingPatternFacts() {
  }

  static Map<String, Object> totals(SpendingAnalysisResponse spending, List<SpendingGameResponse> games,
                                    List<SpendingGameResponse> paid) {
    int gameSpent = paid.stream().mapToInt(FactRows::net).sum();
    double paidHours = paid.stream().mapToDouble(SpendingGameResponse::hours).sum();
    Map<String, Object> totals = new LinkedHashMap<>();
    totals.put("ownedGames", games.size());
    totals.put("paidGames", paid.size());
    totals.put("freeGames", count(games, "FREE"));
    totals.put("noPaymentRecordGames", count(games, "NO_RECORD"));
    totals.put("unknownPriceGames", count(games, "UNKNOWN"));
    // 번들·선물 구매처럼 게임별로 나눌 수 없는 지출도 누적 지출에는 넣는다 (소비 패턴 화면과 같은 기준)
    totals.put("totalSpentKrw", gameSpent + (spending.hasPurchaseHistory() ? spending.unallocatedKrw() : 0));
    totals.put("unallocatedSpentKrw", spending.hasPurchaseHistory() ? spending.unallocatedKrw() : 0);
    totals.put("averagePerPaidGameKrw", paid.isEmpty() ? null : Math.round((double) gameSpent / paid.size()));
    totals.put("totalPlayHours", round1(games.stream().mapToDouble(SpendingGameResponse::hours).sum()));
    totals.put("hourlyCostKrw", paidHours > 0 ? Math.round(gameSpent / paidHours) : null);
    totals.put("savedByDiscountKrw", spending.savedKrw());
    return totals;
  }

  private static long count(List<SpendingGameResponse> games, String source) {
    return games.stream().filter(g -> source.equals(g.amountSource())).count();
  }

  // "내 소비 패턴 분석"에 쓰는 재료: 안 한 게임에 묶인 돈, 한 게임에 쏠린 정도, 구매 가격대, 장르별 가성비, 할인 의존도, 소비 유형.
  // 전부 서버가 계산하고, AI는 {{patterns.…}}로 가리켜 해석만 한다. 유형(profile) 설명에는 숫자를 넣지 않는다.
  static Map<String, Object> patterns(SpendingAnalysisResponse spending, List<SpendingGameResponse> paid,
                                      List<SpendingGameResponse> games) {
    Map<String, Object> result = new LinkedHashMap<>();
    int paidTotal = paid.stream().mapToInt(FactRows::net).sum();

    List<SpendingGameResponse> unplayed = paid.stream().filter(g -> g.hours() < 1).toList();
    int unplayedKrw = unplayed.stream().mapToInt(FactRows::net).sum();
    Long unplayedGamePercent = percent(unplayed.size(), paid.size());
    result.put("unplayed", row("games", unplayed.size(), "spentKrw", unplayedKrw,
        "percentOfPaidGames", unplayedGamePercent,
        "percentOfSpending", paidTotal == 0 ? null : Math.round(unplayedKrw * 100.0 / paidTotal)));

    List<SpendingGameResponse> byHours = games.stream().filter(g -> g.hours() > 0)
        .sorted(Comparator.comparingDouble(SpendingGameResponse::hours).reversed()).toList();
    double totalHours = games.stream().mapToDouble(SpendingGameResponse::hours).sum();
    Long top1Percent = null;
    if (!byHours.isEmpty() && totalHours > 0) {
      top1Percent = Math.round(byHours.get(0).hours() * 100.0 / totalHours);
      double top3 = byHours.stream().limit(3).mapToDouble(SpendingGameResponse::hours).sum();
      result.put("playConcentration", row("topGameName", byHours.get(0).name(), "topGamePercent", top1Percent,
          "top3Percent", Math.round(top3 * 100.0 / totalHours), "topGameHours", round1(byHours.get(0).hours())));
    }

    if (!paid.isEmpty()) {
      long cheap = paid.stream().filter(g -> net(g) < 10_000).count();
      result.put("pricePattern", row("averageKrw", Math.round((double) paidTotal / paid.size()),
          "under10000Games", cheap, "under10000Percent", percent(cheap, paid.size()),
          "over30000Games", paid.stream().filter(g -> net(g) >= 30_000).count()));
    }

    // 장르별 시간당 비용 (1시간 이상 플레이한 장르만): 가장 가성비 좋은/아쉬운 장르
    Map<String, double[]> genres = new LinkedHashMap<>();
    for (SpendingGameResponse g : paid) {
      double[] sums = genres.computeIfAbsent(genre(g), k -> new double[2]);
      sums[0] += net(g);
      sums[1] += g.hours();
    }
    List<Map.Entry<String, double[]>> efficiency = genres.entrySet().stream().filter(e -> e.getValue()[1] >= 1)
        .sorted(Comparator.comparingDouble(e -> e.getValue()[0] / e.getValue()[1])).toList();
    if (efficiency.size() >= 2) {
      Map.Entry<String, double[]> best = efficiency.get(0);
      Map.Entry<String, double[]> worst = efficiency.get(efficiency.size() - 1);
      result.put("bestValueGenre", row("genre", best.getKey(), "hourlyCostKrw", Math.round(best.getValue()[0] / best.getValue()[1])));
      result.put("worstValueGenre", row("genre", worst.getKey(), "hourlyCostKrw", Math.round(worst.getValue()[0] / worst.getValue()[1])));
    }

    Long discountSpendPercent = null;
    if (spending.hasPurchaseHistory()) {
      List<SpendingGameResponse> bought = paid.stream().filter(g -> g.purchasedAt() != null).toList();
      int boughtTotal = bought.stream().mapToInt(FactRows::net).sum();
      int discountedTotal = bought.stream().filter(g -> g.savedKrw() > 0).mapToInt(FactRows::net).sum();
      if (boughtTotal > 0) {
        discountSpendPercent = Math.round(discountedTotal * 100.0 / boughtTotal);
        result.put("discountReliance", row("discountedSpentKrw", discountedTotal, "discountedSpendPercent", discountSpendPercent,
            "savedKrw", spending.savedKrw()));
      }
    }

    result.put("profile", profile(unplayedGamePercent, top1Percent, discountSpendPercent));
    return result;
  }

  // 규칙으로 정하는 소비 유형 (우선순위 순). 설명에는 숫자를 넣지 않아 AI가 숫자를 직접 옮겨 적을 일이 없다.
  private static Map<String, Object> profile(Long unplayedGamePercent, Long top1Percent, Long discountSpendPercent) {
    if (unplayedGamePercent != null && unplayedGamePercent >= 40) {
      return row("name", "수집가형", "meaning", "게임을 사 모으는 속도가 플레이하는 속도보다 빠른 편");
    }
    if (top1Percent != null && top1Percent >= 40) {
      return row("name", "몰입형", "meaning", "여러 게임을 사기보다 마음에 든 한 게임에 시간을 깊게 쏟는 편");
    }
    if (discountSpendPercent != null && discountSpendPercent >= 60) {
      return row("name", "세일 사냥꾼형", "meaning", "할인 때 주로 사서 정가 대비 많이 아끼는 편");
    }
    return row("name", "균형형", "meaning", "구매와 플레이가 비교적 고르게 이어지는 편");
  }

  static List<Map<String, Object>> byGenre(List<SpendingGameResponse> paid) {
    Map<String, double[]> genres = new LinkedHashMap<>();
    for (SpendingGameResponse g : paid) {
      double[] sums = genres.computeIfAbsent(genre(g), k -> new double[3]);
      sums[0] += net(g);
      sums[1]++;
      sums[2] += g.hours();
    }
    int total = paid.stream().mapToInt(FactRows::net).sum();
    return genres.entrySet().stream()
        .sorted((a, b) -> Double.compare(b.getValue()[0], a.getValue()[0]))
        .map(e -> row("genre", e.getKey(), "spentKrw", (int) e.getValue()[0],
            "percent", total == 0 ? 0 : Math.round(e.getValue()[0] * 100 / total),
            "games", (int) e.getValue()[1], "playHours", round1(e.getValue()[2])))
        .toList();
  }

  // 할인으로 산 게임과 정가로 산 게임의 미플레이(1시간 미만) 비율 비교
  static Map<String, Object> discountHabit(List<SpendingGameResponse> paid) {
    List<SpendingGameResponse> bought = paid.stream().filter(g -> g.purchasedAt() != null).toList();
    List<SpendingGameResponse> discounted = bought.stream().filter(g -> g.savedKrw() > 0).toList();
    List<SpendingGameResponse> fullPrice = bought.stream().filter(g -> g.savedKrw() <= 0).toList();
    return row("discountedGames", discounted.size(),
        "discountedUnplayed", unplayedCount(discounted),
        "discountedUnplayedPercent", percent(unplayedCount(discounted), discounted.size()),
        "fullPriceGames", fullPrice.size(),
        "fullPriceUnplayed", unplayedCount(fullPrice),
        "fullPriceUnplayedPercent", percent(unplayedCount(fullPrice), fullPrice.size()),
        // 할인 구매 미플레이 비율 - 정가 구매 미플레이 비율 (양수면 할인 때 산 게임을 더 안 함)
        "unplayedPercentGap", gap(percent(unplayedCount(discounted), discounted.size()),
            percent(unplayedCount(fullPrice), fullPrice.size())));
  }

  private static Long gap(Long a, Long b) {
    return a == null || b == null ? null : a - b;
  }

  private static long unplayedCount(List<SpendingGameResponse> games) {
    return games.stream().filter(g -> g.hours() < 1).count();
  }
}
