package org.ict.final_project_back_end.domain.aicoach.service;

import org.ict.final_project_back_end.domain.analysis.dto.SpendingGameResponse;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.ict.final_project_back_end.domain.aicoach.service.FactRows.date;
import static org.ict.final_project_back_end.domain.aicoach.service.FactRows.genre;
import static org.ict.final_project_back_end.domain.aicoach.service.FactRows.net;
import static org.ict.final_project_back_end.domain.aicoach.service.FactRows.round1;
import static org.ict.final_project_back_end.domain.aicoach.service.FactRows.row;

// 게임 목록 facts: 안 한 유료 게임 · 가성비 좋은/아쉬운 게임 · 환불 가능한 게임 · 요즘 하는 게임 · 추천 후보 보유 게임 표.
final class GameListFacts {

  static final int MAX_GAMES = 150;
  private static final int LIST_SIZE = 5;
  private static final int MAX_UNPLAYED = 15;
  // 스팀 환불 기준: 구매 후 14일 이내, 플레이 2시간 미만
  private static final int REFUND_DAYS = 14;
  private static final double REFUND_HOURS = 2;

  private GameListFacts() {
  }

  static List<Map<String, Object>> unplayed(List<SpendingGameResponse> paid) {
    return paid.stream().filter(g -> g.hours() < 1)
        .sorted(Comparator.comparingInt(FactRows::net).reversed())
        .limit(MAX_UNPLAYED)
        .map(g -> row("appId", g.appId(), "name", g.name(), "genre", genre(g), "amountKrw", net(g),
            "playMinutes", Math.round(g.hours() * 60), "purchasedAt", date(g.purchasedAt())))
        .toList();
  }

  // 시간당 비용 순위. 1시간 이상 플레이한 유료 게임만 (몇 분 켜본 게임이 순위를 흔들지 않게)
  static List<Map<String, Object>> valueRanking(List<SpendingGameResponse> paid, boolean best) {
    Comparator<SpendingGameResponse> byHourly = Comparator.comparingDouble(g -> net(g) / g.hours());
    return paid.stream().filter(g -> g.hours() >= 1)
        .sorted(best ? byHourly : byHourly.reversed())
        .limit(LIST_SIZE)
        .map(g -> row("appId", g.appId(), "name", g.name(), "amountKrw", net(g), "playHours", round1(g.hours()),
            "hourlyCostKrw", Math.round(net(g) / g.hours())))
        .toList();
  }

  static List<Map<String, Object>> refundable(List<SpendingGameResponse> paid, LocalDate today) {
    return paid.stream()
        .filter(g -> g.purchasedAt() != null && g.hours() < REFUND_HOURS
            && !g.purchasedAt().isBefore(today.minusDays(REFUND_DAYS - 1)) && !g.purchasedAt().isAfter(today))
        .map(g -> row("appId", g.appId(), "name", g.name(), "amountKrw", net(g), "purchasedAt", g.purchasedAt().toString(),
            "playMinutes", Math.round(g.hours() * 60), "refundDeadline", g.purchasedAt().plusDays(REFUND_DAYS).toString()))
        .toList();
  }

  static List<Map<String, Object>> recent(List<SpendingGameResponse> games) {
    return games.stream().filter(g -> g.recentHours() > 0)
        .sorted(Comparator.comparingDouble(SpendingGameResponse::recentHours).reversed())
        .limit(LIST_SIZE)
        .map(g -> row("appId", g.appId(), "name", g.name(), "hoursLast2Weeks", round1(g.recentHours())))
        .toList();
  }

  // 추천 후보가 되는 보유 게임 표. 많이 한 게임, 비싼 게임 순으로 최대 MAX_GAMES개
  static Map<String, Map<String, Object>> ownedGames(List<SpendingGameResponse> games) {
    return games.stream()
        .sorted(Comparator.comparingDouble(SpendingGameResponse::hours).reversed()
            .thenComparing(Comparator.comparingInt(FactRows::net).reversed()))
        .limit(MAX_GAMES)
        .map(g -> {
          Map<String, Object> row = new LinkedHashMap<>();
          row.put("appId", g.appId());
          row.put("name", g.name());
          row.put("genres", g.genres().stream().map(FactRows::label).toList());
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
}
