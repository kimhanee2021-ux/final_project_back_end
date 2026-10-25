package org.ict.final_project_back_end.domain.aicoach.service;

import org.ict.final_project_back_end.domain.analysis.dto.SpendingGameResponse;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

// 코치 facts를 만드는 클래스들이 함께 쓰는 계산·표현 규칙.
// 금액 규칙은 소비 패턴 화면과 같다: 환불을 뺀 금액, 장르는 한국어 이름, 소수는 첫째 자리까지.
final class FactRows {

  private static final Map<String, String> GENRE_LABELS = Map.ofEntries(
      Map.entry("Action", "액션"), Map.entry("Adventure", "어드벤처"), Map.entry("RPG", "RPG"),
      Map.entry("Strategy", "전략"), Map.entry("Simulation", "시뮬레이션"), Map.entry("Sports", "스포츠"),
      Map.entry("Racing", "레이싱"), Map.entry("Casual", "캐주얼"), Map.entry("Massively Multiplayer", "MMO"),
      Map.entry("Indie", "인디"), Map.entry("Free To Play", "무료"), Map.entry("Early Access", "앞서 해보기"));

  private FactRows() {
  }

  // 환불을 뺀 실제 지출 (금액을 모르면 0)
  static int net(SpendingGameResponse game) {
    return game.amount() == null ? 0 : Math.max(0, game.amount() - game.refund());
  }

  static String genre(SpendingGameResponse game) {
    return game.genre() == null ? "기타" : label(game.genre());
  }

  static String label(String genre) {
    return GENRE_LABELS.getOrDefault(genre, genre);
  }

  static String date(LocalDate date) {
    return date == null ? null : date.toString();
  }

  static double round1(double value) {
    return Math.round(value * 10) / 10.0;
  }

  static Long percent(long part, int whole) {
    return whole == 0 ? null : Math.round(part * 100.0 / whole);
  }

  // 키·값을 번갈아 받아 순서를 지키는 한 줄짜리 표를 만든다
  static Map<String, Object> row(Object... keyValues) {
    Map<String, Object> row = new LinkedHashMap<>();
    for (int i = 0; i < keyValues.length; i += 2) {
      row.put((String) keyValues[i], keyValues[i + 1]);
    }
    return row;
  }
}
