package org.ict.final_project_back_end.domain.aiCoach.service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// AI 답변 속 금액("15,500원")이 서버가 계산한 facts의 금액과 정확히 같은지 확인한다.
// 가벼운 모델은 숫자를 옮겨 적다 틀릴 때가 있어서(15,500원 -> 15,400원), 소비 코치에서는 이런 답을 그대로 보여주지 않는다.
public final class CoachAnswerGuard {

  // "15,500원", "300000원", "4,422 원" (만 단위 표현 "3만 원"은 facts에 없는 반올림 표현이라 검사하지 않는다)
  private static final Pattern WON = Pattern.compile("(?<![\\d.,])(\\d{1,3}(?:,\\d{3})+|\\d+)\\s*원");

  private CoachAnswerGuard() {
  }

  // facts에 없는 금액 목록 (비어 있으면 통과)
  public static Set<Long> unknownAmounts(String answer, Map<String, Object> facts) {
    Set<Long> known = new TreeSet<>();
    collectNumbers(facts, known);
    Set<Long> unknown = new LinkedHashSet<>();
    Matcher matcher = WON.matcher(answer == null ? "" : answer);
    while (matcher.find()) {
      long amount = Long.parseLong(matcher.group(1).replace(",", ""));
      // 0원은 "이번 달 0원"처럼 facts에 없어도 자연스러운 표현이라 허용
      if (amount != 0 && !known.contains(amount)) {
        unknown.add(amount);
      }
    }
    return unknown;
  }

  private static void collectNumbers(Object value, Set<Long> into) {
    if (value instanceof Number number) {
      into.add(Math.round(number.doubleValue()));
    } else if (value instanceof Map<?, ?> map) {
      map.values().forEach(v -> collectNumbers(v, into));
    } else if (value instanceof Collection<?> list) {
      list.forEach(v -> collectNumbers(v, into));
    }
  }

}
