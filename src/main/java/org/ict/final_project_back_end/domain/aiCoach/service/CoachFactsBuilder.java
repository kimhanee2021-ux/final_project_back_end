package org.ict.final_project_back_end.domain.aiCoach.service;

import org.ict.final_project_back_end.domain.analysis.dto.SpendingAnalysisResponse;
import org.ict.final_project_back_end.domain.analysis.dto.SpendingGameResponse;
import org.ict.final_project_back_end.domain.budget.dto.BudgetResponse;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// AI 소비 코치에게 줄 "사실" 묶음을 만든다. 합계·비율·순위는 모두 여기서 계산하고,
// AI는 이 숫자를 고르고 설명만 한다 (AI가 직접 더하거나 나누다 틀리지 않게).
// 금액 규칙은 소비 패턴 화면과 같다: 환불을 뺀 금액, 1시간 미만은 미플레이, 가성비는 1시간 이상 게임끼리 비교.
public final class CoachFactsBuilder {

  static final int MAX_GAMES = 150;
  private static final int LIST_SIZE = 5;
  private static final int MAX_UNPLAYED = 15;
  // 스팀 환불 기준: 구매 후 14일 이내, 플레이 2시간 미만
  private static final int REFUND_DAYS = 14;
  private static final double REFUND_HOURS = 2;

  private static final Map<String, String> GENRE_LABELS = Map.ofEntries(
      Map.entry("Action", "액션"), Map.entry("Adventure", "어드벤처"), Map.entry("RPG", "RPG"),
      Map.entry("Strategy", "전략"), Map.entry("Simulation", "시뮬레이션"), Map.entry("Sports", "스포츠"),
      Map.entry("Racing", "레이싱"), Map.entry("Casual", "캐주얼"), Map.entry("Massively Multiplayer", "MMO"),
      Map.entry("Indie", "인디"), Map.entry("Free To Play", "무료"), Map.entry("Early Access", "앞서 해보기"));

  private CoachFactsBuilder() {
  }

  public static Map<String, Object> build(SpendingAnalysisResponse spending, BudgetResponse budget, LocalDate today) {
    List<SpendingGameResponse> games = spending.games();
    List<SpendingGameResponse> paid = games.stream().filter(g -> net(g) > 0).toList();
    boolean history = spending.hasPurchaseHistory();

    Map<String, Object> facts = new LinkedHashMap<>();
    facts.put("today", today.toString());
    facts.put("amountBasis", history
        ? "실제 결제액 (스팀 구매내역, 해외 통화는 구매일 환율로 원화 환산)"
        : "현재 한국 스팀 정가로 추정한 금액 (구매내역을 아직 가져오지 않음, 실제 결제액·구매일 없음)");
    facts.put("totals", totals(spending, games, paid));
    if (history) {
      Map<String, Map<String, Object>> years = byYear(spending.transactions(), today.getYear());
      facts.put("spendingByYear", years);
      Map<String, Map<String, Object>> months = byMonth(spending.transactions(), today);
      facts.put("spendingByMonthLast12", months);
      facts.put("comparisons", comparisons(years, months, today.getYear()));
    }
    facts.put("thisMonth", thisMonth(spending, budget, today));
    facts.put("highlights", highlights(spending, paid, games, today));
    facts.put("patterns", patterns(spending, paid, games));
    facts.put("spendingByGenre", byGenre(paid));
    if (history) {
      facts.put("discountHabit", discountHabit(paid));
    }
    facts.put("unplayedPaidGames", unplayed(paid));
    facts.put("bestValueGames", valueRanking(paid, true));
    facts.put("worstValueGames", valueRanking(paid, false));
    if (history) {
      facts.put("refundableGames", refundable(paid, today));
    }
    facts.put("playingRecently", recent(games));
    facts.put("ownedGames", ownedGames(games));
    return facts;
  }

  static int net(SpendingGameResponse game) {
    return game.amount() == null ? 0 : Math.max(0, game.amount() - game.refund());
  }

  private static Map<String, Object> totals(SpendingAnalysisResponse spending, List<SpendingGameResponse> games,
                                            List<SpendingGameResponse> paid) {
    int gameSpent = paid.stream().mapToInt(CoachFactsBuilder::net).sum();
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

  // relative: AI가 "작년"을 엉뚱한 해로 읽지 않게 올해(진행 중)/작년/N년 전을 함께 적는다
  private static String relativeYear(int year, int thisYear) {
    int diff = thisYear - year;
    return diff == 0 ? "올해(진행 중)" : diff == 1 ? "작년" : diff + "년 전";
  }

  private static Map<String, Map<String, Object>> byYear(List<SpendingAnalysisResponse.PurchaseView> transactions, int thisYear) {
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

  private static Map<String, Map<String, Object>> byMonth(List<SpendingAnalysisResponse.PurchaseView> transactions, LocalDate today) {
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
  private static Map<String, Object> highlights(SpendingAnalysisResponse spending, List<SpendingGameResponse> paid,
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
    paid.stream().max(Comparator.comparingInt(CoachFactsBuilder::net))
        .ifPresent(g -> result.put("mostExpensiveGame", row("appId", g.appId(), "name", g.name(), "amountKrw", net(g))));
    games.stream().filter(g -> g.hours() > 0).max(Comparator.comparingDouble(SpendingGameResponse::hours))
        .ifPresent(g -> result.put("mostPlayedGame", row("appId", g.appId(), "name", g.name(), "playHours", round1(g.hours()))));
    return result;
  }

  // AI가 직접 빼거나 나누지 않도록 자주 묻는 비교를 미리 계산한다 (차이는 절댓값 + direction)
  private static Map<String, Object> comparisons(Map<String, Map<String, Object>> years,
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

  // "내 소비 패턴 분석"에 쓰는 재료: 안 한 게임에 묶인 돈, 한 게임에 쏠린 정도, 구매 가격대, 장르별 가성비, 할인 의존도, 소비 유형.
  // 전부 서버가 계산하고, AI는 {{patterns.…}}로 가리켜 해석만 한다. 유형(profile) 설명에는 숫자를 넣지 않는다.
  private static Map<String, Object> patterns(SpendingAnalysisResponse spending, List<SpendingGameResponse> paid,
                                              List<SpendingGameResponse> games) {
    Map<String, Object> result = new LinkedHashMap<>();
    int paidTotal = paid.stream().mapToInt(CoachFactsBuilder::net).sum();

    List<SpendingGameResponse> unplayed = paid.stream().filter(g -> g.hours() < 1).toList();
    int unplayedKrw = unplayed.stream().mapToInt(CoachFactsBuilder::net).sum();
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
      int boughtTotal = bought.stream().mapToInt(CoachFactsBuilder::net).sum();
      int discountedTotal = bought.stream().filter(g -> g.savedKrw() > 0).mapToInt(CoachFactsBuilder::net).sum();
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

  private static Map<String, Object> thisMonth(SpendingAnalysisResponse spending, BudgetResponse budget, LocalDate today) {
    String month = today.toString().substring(0, 7);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("month", month);
    Integer spent = null;
    if (spending.hasPurchaseHistory()) {
      spent = Math.max(0, spending.transactions().stream()
          .filter(t -> t.purchasedAt().toString().startsWith(month))
          .mapToInt(SpendingAnalysisResponse.PurchaseView::amountKrw).sum());
    }
    result.put("spentKrw", spent);
    Integer limit = budget == null ? null : budget.limitAmount();
    result.put("budgetKrw", limit);
    result.put("remainingBudgetKrw", limit != null && spent != null ? limit - spent : null);
    if (spent == null) {
      result.put("note", "구매내역을 가져오지 않아 이번 달 실제 지출을 알 수 없음");
    }
    return result;
  }

  private static List<Map<String, Object>> byGenre(List<SpendingGameResponse> paid) {
    Map<String, double[]> genres = new LinkedHashMap<>();
    for (SpendingGameResponse g : paid) {
      double[] sums = genres.computeIfAbsent(genre(g), k -> new double[3]);
      sums[0] += net(g);
      sums[1]++;
      sums[2] += g.hours();
    }
    int total = paid.stream().mapToInt(CoachFactsBuilder::net).sum();
    return genres.entrySet().stream()
        .sorted((a, b) -> Double.compare(b.getValue()[0], a.getValue()[0]))
        .map(e -> row("genre", e.getKey(), "spentKrw", (int) e.getValue()[0],
            "percent", total == 0 ? 0 : Math.round(e.getValue()[0] * 100 / total),
            "games", (int) e.getValue()[1], "playHours", round1(e.getValue()[2])))
        .toList();
  }

  // 할인으로 산 게임과 정가로 산 게임의 미플레이(1시간 미만) 비율 비교
  private static Map<String, Object> discountHabit(List<SpendingGameResponse> paid) {
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

  private static Long percent(long part, int whole) {
    return whole == 0 ? null : Math.round(part * 100.0 / whole);
  }

  private static List<Map<String, Object>> unplayed(List<SpendingGameResponse> paid) {
    return paid.stream().filter(g -> g.hours() < 1)
        .sorted(Comparator.comparingInt(CoachFactsBuilder::net).reversed())
        .limit(MAX_UNPLAYED)
        .map(g -> row("appId", g.appId(), "name", g.name(), "genre", genre(g), "amountKrw", net(g),
            "playMinutes", Math.round(g.hours() * 60), "purchasedAt", date(g.purchasedAt())))
        .toList();
  }

  // 시간당 비용 순위. 1시간 이상 플레이한 유료 게임만 (몇 분 켜본 게임이 순위를 흔들지 않게)
  private static List<Map<String, Object>> valueRanking(List<SpendingGameResponse> paid, boolean best) {
    Comparator<SpendingGameResponse> byHourly = Comparator.comparingDouble(g -> net(g) / g.hours());
    return paid.stream().filter(g -> g.hours() >= 1)
        .sorted(best ? byHourly : byHourly.reversed())
        .limit(LIST_SIZE)
        .map(g -> row("appId", g.appId(), "name", g.name(), "amountKrw", net(g), "playHours", round1(g.hours()),
            "hourlyCostKrw", Math.round(net(g) / g.hours())))
        .toList();
  }

  private static List<Map<String, Object>> refundable(List<SpendingGameResponse> paid, LocalDate today) {
    return paid.stream()
        .filter(g -> g.purchasedAt() != null && g.hours() < REFUND_HOURS
            && !g.purchasedAt().isBefore(today.minusDays(REFUND_DAYS - 1)) && !g.purchasedAt().isAfter(today))
        .map(g -> row("appId", g.appId(), "name", g.name(), "amountKrw", net(g), "purchasedAt", g.purchasedAt().toString(),
            "playMinutes", Math.round(g.hours() * 60), "refundDeadline", g.purchasedAt().plusDays(REFUND_DAYS).toString()))
        .toList();
  }

  private static List<Map<String, Object>> recent(List<SpendingGameResponse> games) {
    return games.stream().filter(g -> g.recentHours() > 0)
        .sorted(Comparator.comparingDouble(SpendingGameResponse::recentHours).reversed())
        .limit(LIST_SIZE)
        .map(g -> row("appId", g.appId(), "name", g.name(), "hoursLast2Weeks", round1(g.recentHours())))
        .toList();
  }

  // 추천 후보가 되는 보유 게임 표. 많이 한 게임, 비싼 게임 순으로 최대 MAX_GAMES개
  private static Map<String, Map<String, Object>> ownedGames(List<SpendingGameResponse> games) {
    return games.stream()
        .sorted(Comparator.comparingDouble(SpendingGameResponse::hours).reversed()
            .thenComparing(Comparator.comparingInt(CoachFactsBuilder::net).reversed()))
        .limit(MAX_GAMES)
        .map(g -> {
          Map<String, Object> row = new LinkedHashMap<>();
          row.put("appId", g.appId());
          row.put("name", g.name());
          row.put("genres", g.genres().stream().map(CoachFactsBuilder::label).toList());
          row.put("amountKrw", g.amount() == null ? null : net(g));
          row.put("amountSource", g.amountSource());
          row.put("playHours", round1(g.hours()));
          row.put("hoursLast2Weeks", round1(g.recentHours()));
          row.put("purchasedAt", date(g.purchasedAt()));
          row.put("boughtOnDiscount", g.savedKrw() > 0);
          row.put("dlcCount", g.dlcCount());
          row.put("acquisitionMethod", g.acquisitionMethod());
          return row;
        })
        .collect(LinkedHashMap<String, Map<String, Object>>::new,
            (map, row) -> map.put(String.valueOf(row.get("appId")), row), Map::putAll);
  }

  private static String genre(SpendingGameResponse game) {
    return game.genre() == null ? "기타" : label(game.genre());
  }

  private static String label(String genre) {
    return GENRE_LABELS.getOrDefault(genre, genre);
  }

  private static String date(LocalDate date) {
    return date == null ? null : date.toString();
  }

  private static double round1(double value) {
    return Math.round(value * 10) / 10.0;
  }

  private static Map<String, Object> row(Object... keyValues) {
    Map<String, Object> row = new LinkedHashMap<>();
    for (int i = 0; i < keyValues.length; i += 2) {
      row.put((String) keyValues[i], keyValues[i + 1]);
    }
    return row;
  }
}
