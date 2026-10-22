package org.ict.final_project_back_end.domain.aiCoach.service;

import org.ict.final_project_back_end.domain.aiCoach.dto.CoachChatResponse;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// AI 답변의 숫자 자리표시자({{totals.totalSpentKrw}})를 facts의 실제 값으로 바꾸고, 쓰인 값을 "근거"로 모은다.
// AI는 숫자를 직접 옮겨 적지 않으므로 15,500원 -> 15,400원 같은 실수가 생기지 않는다.
// 없는 칸을 가리키거나, 자리표시자 없이 숫자를 직접 쓰면 problems에 담아 서비스가 다시 묻게 한다.
public final class CoachAnswerRenderer {

  public record Rendered(String text, List<CoachChatResponse.Evidence> evidence, List<String> problems) {
    public boolean ok() {
      return problems.isEmpty();
    }
  }

  private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.\\-]+)\\s*}}");
  // 자리표시자 밖에 직접 쓴 "단위가 붙은 숫자" (금액·시간·분·비율·연도). 질문에 있던 숫자는 허용한다
  private static final Pattern RAW_NUMBER = Pattern.compile("(\\d[\\d,]*(?:\\.\\d+)?)\\s*(원|시간|분|%|퍼센트|년)");
  // 서비스 규칙을 설명할 때 쓰는 고정 표현 ("1시간 이상 플레이", "2시간 미만이면 환불")은 데이터 숫자가 아니라 허용
  private static final Set<String> RULE_PHRASES = Set.of("1시간", "2시간");
  private static final Pattern YEAR_MONTH = Pattern.compile("(\\d{4})-(\\d{2})");
  private static final Pattern DATE =Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})");

  // 근거 보기에 보여줄 칸 이름
  private static final Map<String, String> FIELD_LABELS = Map.ofEntries(
      Map.entry("totalSpentKrw", "누적 지출"), Map.entry("savedByDiscountKrw", "할인으로 아낀 금액"),
      Map.entry("hourlyCostKrw", "시간당 비용"), Map.entry("averagePerPaidGameKrw", "게임당 평균 금액"),
      Map.entry("unallocatedSpentKrw", "게임별로 나눌 수 없는 지출"), Map.entry("totalPlayHours", "총 플레이 시간"),
      Map.entry("ownedGames", "보유 게임 수"), Map.entry("paidGames", "유료 게임 수"), Map.entry("freeGames", "무료 게임 수"),
      Map.entry("noPaymentRecordGames", "구매 기록 없는 게임 수"), Map.entry("unknownPriceGames", "가격 모르는 게임 수"),
      Map.entry("spentKrw", "지출"), Map.entry("purchases", "구매 건수"), Map.entry("budgetKrw", "예산"),
      Map.entry("remainingBudgetKrw", "남은 예산"), Map.entry("percent", "비율"), Map.entry("games", "게임 수"),
      Map.entry("playHours", "플레이 시간"), Map.entry("amountKrw", "금액"), Map.entry("playMinutes", "플레이 시간"),
      Map.entry("hoursLast2Weeks", "최근 2주 플레이"), Map.entry("refundDeadline", "환불 기한"),
      Map.entry("purchasedAt", "구매일"), Map.entry("discountedGames", "할인 구매 게임 수"),
      Map.entry("discountedUnplayed", "할인 구매 중 안 한 게임 수"), Map.entry("discountedUnplayedPercent", "할인 구매 미플레이 비율"),
      Map.entry("fullPriceGames", "정가 구매 게임 수"), Map.entry("fullPriceUnplayed", "정가 구매 중 안 한 게임 수"),
      Map.entry("fullPriceUnplayedPercent", "정가 구매 미플레이 비율"), Map.entry("unplayedPercentGap", "미플레이 비율 차이(할인-정가)"),
      Map.entry("thisYearSpentKrw", "올해 지출(진행 중)"), Map.entry("lastYearSpentKrw", "작년 지출"),
      Map.entry("changeKrw", "지출 차이"), Map.entry("changePercent", "지출 변화율"),
      Map.entry("averagePerMonthKrw", "월평균 지출"), Map.entry("dlcCount", "DLC 수"), Map.entry("name", "이름"),
      Map.entry("thisYear", "올해"), Map.entry("lastYear", "작년"), Map.entry("direction", "변화"),
      Map.entry("year", "연도"), Map.entry("month", "월"),
      Map.entry("percentOfPaidGames", "유료 게임 중 비율"), Map.entry("percentOfSpending", "전체 지출 중 비율"),
      Map.entry("topGamePercent", "가장 오래 한 게임 비중"), Map.entry("top3Percent", "상위 3개 게임 비중"),
      Map.entry("topGameHours", "가장 오래 한 게임 시간"), Map.entry("averageKrw", "평균 구매 금액"),
      Map.entry("under10000Games", "1만 원 미만 구매 수"), Map.entry("under10000Percent", "1만 원 미만 구매 비율"),
      Map.entry("over30000Games", "3만 원 이상 구매 수"), Map.entry("discountedSpentKrw", "할인 구매 지출"),
      Map.entry("discountedSpendPercent", "할인 구매 지출 비중"), Map.entry("savedKrw", "아낀 금액"),
      Map.entry("recentSpentKrw", "최근 3개월 지출"), Map.entry("previousSpentKrw", "그 전 3개월 지출"));

  private static final Map<String, String> SECTION_LABELS = Map.ofEntries(
      Map.entry("totals", ""), Map.entry("thisMonth", "이번 달"), Map.entry("highlights", ""),
      Map.entry("mostSpentYear", "가장 많이 쓴 해"), Map.entry("mostSpentMonth", "가장 많이 쓴 달"),
      Map.entry("mostExpensiveGame", ""), Map.entry("mostPlayedGame", ""),
      Map.entry("patterns", ""), Map.entry("unplayed", "안 한 게임"), Map.entry("playConcentration", "플레이 집중도"),
      Map.entry("pricePattern", "구매 가격대"), Map.entry("discountReliance", "할인 의존도"), Map.entry("profile", "소비 유형"),
      Map.entry("recent3VsPrevious3Months", "3개월 추세"), Map.entry("discountHabit", ""),
      Map.entry("comparisons", ""), Map.entry("thisYearVsLastYear", "작년 대비 올해"), Map.entry("last12Months", "최근 12개월"),
      Map.entry("spendingByGenre", ""), Map.entry("unplayedPaidGames", ""), Map.entry("bestValueGames", ""),
      Map.entry("worstValueGames", ""), Map.entry("refundableGames", ""), Map.entry("playingRecently", ""),
      Map.entry("ownedGames", ""), Map.entry("spendingByYear", ""), Map.entry("spendingByMonthLast12", ""));

  private CoachAnswerRenderer() {
  }

  public static Rendered render(String answer, Map<String, Object> facts, String question) {
    List<String> problems = new ArrayList<>();
    Map<String, CoachChatResponse.Evidence> evidence = new LinkedHashMap<>();
    StringBuilder out = new StringBuilder();
    Matcher matcher = PLACEHOLDER.matcher(answer == null ? "" : answer);
    int last = 0;
    StringBuilder outside = new StringBuilder();
    while (matcher.find()) {
      outside.append(answer, last, matcher.start()).append(' ');
      out.append(answer, last, matcher.start());
      String path = matcher.group(1);
      Resolved resolved = resolve(facts, path);
      if (resolved == null || resolved.value() == null) {
        problems.add("없는 칸: " + path);
        out.append(matcher.group());
      } else {
        String shown = format(resolved.field(), resolved.value());
        out.append(shown);
        if (!isLabelField(resolved.field())) {
          evidence.putIfAbsent(path, evidenceOf(facts, path, resolved, shown));
        }
        // AI는 값의 받침을 모르니 바로 뒤 조사를 실제 값에 맞춘다 ("11,117원를" -> "11,117원을", "101원예요" -> "101원이에요")
        int skip = fixParticle(shown, answer, matcher.end(), out);
        last = matcher.end() + skip;
        continue;
      }
      last = matcher.end();
    }
    out.append(answer == null ? "" : answer.substring(last));
    outside.append(answer == null ? "" : answer.substring(last));

    String asked = question == null ? "" : question.replace(",", "");
    Matcher raw = RAW_NUMBER.matcher(outside);
    Map<String, String> known = null;
    while (raw.find()) {
      String number = raw.group(1).replace(",", "");
      if (!number.equals("0") && !asked.contains(number) && !RULE_PHRASES.contains(number + raw.group(2))) {
        // AI가 자리표시자 대신 숫자를 직접 썼더라도 facts에 있는 값과 글자까지 똑같으면 옮겨 적다 틀린 게 아니므로 받아 준다
        // ("63.4시간"처럼). 근거 보기에도 그 칸을 넣는다. 어느 칸과도 다르면(15,500원을 15,400원으로 쓴 경우 등) 지금처럼 실패다.
        if (known == null) {
          known = knownValues(facts);
        }
        String path = known.get(raw.group(1) + raw.group(2));
        if (path != null) {
          Resolved resolved = resolve(facts, path);
          if (resolved != null && !isLabelField(resolved.field())) {
            evidence.putIfAbsent(path, evidenceOf(facts, path, resolved, format(resolved.field(), resolved.value())));
          }
        } else {
          problems.add("직접 쓴 숫자: " + raw.group());
        }
      }
    }
    // "{{x}}원"처럼 단위를 한 번 더 붙인 경우 정리
    String text = out.toString().replace("원원", "원").replace("년년", "년").replace("월월", "월")
        // AI가 연도를 앞에 따로 쓰고 월 값(연도 포함)을 또 붙인 경우: "2024년 2024년 9월" -> "2024년 9월"
        .replaceAll("(\\d{4})년 \\1년 (\\d{1,2}월)", "$1년 $2").replace("시간시간", "시간").replace("분분", "분")
        .replace("%%", "%").replace("%퍼센트", "%");
    text = fixBracketParticles(text);
    return new Rendered(text, List.copyOf(evidence.values()), List.copyOf(problems));
  }

  // 근거 한 줄: 막대로 그릴 수 있는 숫자(원·시간·분·%)면 숫자와 단위, 같은 그래프에 묶을 group을 같이 담는다
  private static CoachChatResponse.Evidence evidenceOf(Map<String, Object> facts, String path, Resolved resolved, String shown) {
    String label = describe(facts, path);
    String unit = resolved.value() instanceof Number ? chartUnit(resolved.field()) : null;
    if (unit == null) {
      return new CoachChatResponse.Evidence(label, shown);
    }
    return new CoachChatResponse.Evidence(label, shown, ((Number) resolved.value()).doubleValue(), unit, groupOf(path));
  }

  private static String chartUnit(String field) {
    if (field.endsWith("Krw")) {
      return "원";
    }
    if (field.endsWith("Hours") || field.equals("playHours") || field.equals("hoursLast2Weeks")) {
      return "시간";
    }
    if (field.endsWith("Minutes")) {
      return "분";
    }
    if (field.endsWith("Percent") || field.equals("percent") || field.startsWith("percentOf") || field.endsWith("PercentGap")) {
      return "%";
    }
    return null;
  }

  // 같은 표의 여러 줄(연도·순번)은 한 묶음: "spendingByYear.2025.spentKrw" -> "spendingByYear.*.spentKrw", 한 표 안의 다른 칸은 "comparisons.thisYearVsLastYear"
  static String groupOf(String path) {
    String[] parts = path.split("\\.");
    StringBuilder parent = new StringBuilder();
    boolean repeated = false;
    for (int i = 0; i < parts.length - 1; i++) {
      boolean index = parts[i].matches("\\d+|\\d{4}-\\d{2}");
      repeated |= index;
      parent.append(i == 0 ? "" : ".").append(index ? "*" : parts[i]);
    }
    return repeated ? parent + "." + parts[parts.length - 1] : parent.toString();
  }

  // facts의 모든 숫자 칸을 화면에 나오는 모양("63.4시간", "87,765원")으로 바꿔 그 칸의 경로를 찾을 수 있게 모은다 (같은 모양이 여러 칸이면 먼저 나온 칸)
  private static Map<String, String> knownValues(Map<String, Object> facts) {
    Map<String, String> known = new LinkedHashMap<>();
    collectKnown(facts, "", known);
    return known;
  }

  private static void collectKnown(Object node, String path, Map<String, String> known) {
    if (node instanceof Map<?, ?> map) {
      map.forEach((key, value) -> collectKnown(value, path.isEmpty() ? String.valueOf(key) : path + "." + key, known));
    } else if (node instanceof List<?> list) {
      for (int i = 0; i < list.size(); i++) {
        collectKnown(list.get(i), path + "." + i, known);
      }
    } else if (node instanceof Number) {
      String field = path.substring(path.lastIndexOf('.') + 1);
      known.putIfAbsent(format(field, node).replace(" ", ""), path);
    }
  }

  // 게임 이름 같은 글자 뒤에 AI가 괄호 조사("Rebirth은(는)", "사이버펑크이(가)")를 남긴 경우 앞 글자의 받침에 맞는 하나로 바꾼다.
  // 한글은 받침 유무, 숫자는 읽는 소리(0·1·3·6·7·8은 받침 있음), 영문·기호는 받침 없음("는/를/가/와")으로 본다.
  private static final Pattern BRACKET_PARTICLE = Pattern.compile("(\\S)(이\\(가\\)|은\\(는\\)|을\\(를\\)|과\\(와\\))");

  static String fixBracketParticles(String text) {
    Matcher matcher = BRACKET_PARTICLE.matcher(text);
    StringBuilder out = new StringBuilder();
    while (matcher.find()) {
      char last = matcher.group(1).charAt(0);
      boolean batchim = last >= 0xAC00 && last <= 0xD7A3 ? (last - 0xAC00) % 28 != 0 : "013678".indexOf(last) >= 0;
      String pair = matcher.group(2);
      String chosen = batchim ? pair.substring(0, 1) : pair.substring(2, 3);
      matcher.appendReplacement(out, Matcher.quoteReplacement(last + chosen));
    }
    matcher.appendTail(out);
    return out.toString();
  }

  private record Resolved(String field, Object value) {
  }

  // 이름·장르·설명 같은 글자 값은 숫자 근거가 아니라서 "근거 보기"에 넣지 않는다
  private static boolean isLabelField(String field) {
    return field.equals("name") || field.equals("genre") || field.equals("relative") || field.equals("meaning")
        || field.equals("appId") || field.endsWith("Name");
  }

  // 받침 유무에 따라 바뀌는 조사 쌍 (받침 있을 때, 없을 때). 긴 것부터 검사한다
  private static final String[][] PARTICLES = {{"이에요", "예요"}, {"이었", "였"}, {"을", "를"}, {"은", "는"}, {"과", "와"}};

  private static boolean isHangul(char c) {
    return c >= 0xAC00 && c <= 0xD7A3;
  }

  // 값 뒤의 조사를 받침에 맞게 out에 붙이고, 원문에서 건너뛸 글자 수를 돌려준다. 한글로 끝나지 않는 값(영문 이름 등)은 그대로 둔다
  private static int fixParticle(String shown, String answer, int from, StringBuilder out) {
    char lastChar = shown.isEmpty() ? ' ' : shown.charAt(shown.length() - 1);
    boolean hangul = lastChar >= 0xAC00 && lastChar <= 0xD7A3;
    boolean percent = lastChar == '%';
    if (!hangul && !percent) {
      return 0;
    }
    // "%"는 "퍼센트"로 읽으니 받침 없음
    int jong = hangul ? (lastChar - 0xAC00) % 28 : 0;
    boolean batchim = jong != 0;
    String rest = answer.substring(from).stripLeading().equals(answer.substring(from)) ? answer.substring(from) : null;
    if (rest == null) {
      return 0;
    }
    // 로/으로: 받침이 있으면 "으로", 단 ㄹ 받침(8)은 "로" ("129원으로", "48분으로", "2026년 10월 24일로")
    for (String written : new String[]{"으로", "로"}) {
      if (rest.startsWith(written)) {
        out.append(batchim && jong != 8 ? "으로" : "로");
        return written.length();
      }
    }
    // AI가 "이(가)", "을(를)"처럼 괄호로 둘 다 쓴 경우: 받침에 맞는 하나만 남긴다
    for (String[] pair : new String[][]{{"이(가)", "이", "가"}, {"가(이)", "이", "가"}, {"을(를)", "을", "를"}, {"를(을)", "을", "를"},
        {"은(는)", "은", "는"}, {"는(은)", "은", "는"}, {"과(와)", "과", "와"}, {"와(과)", "과", "와"}}) {
      if (rest.startsWith(pair[0])) {
        out.append(batchim ? pair[1] : pair[2]);
        return pair[0].length();
      }
    }
    // 주격 이/가: 뒤에 한글이 이어지면 다른 낱말("이번", "가장")일 수 있어서 공백·문장부호·끝일 때만 고친다
    if ((rest.startsWith("이") || rest.startsWith("가")) && !rest.startsWith("이에요")
        && (rest.length() == 1 || !isHangul(rest.charAt(1)))) {
      out.append(batchim ? "이" : "가");
      return 1;
    }
    for (String[] pair : PARTICLES) {
      for (String written : pair) {
        if (rest.startsWith(written)) {
          out.append(batchim ? pair[0] : pair[1]);
          return written.length();
        }
      }
    }
    return 0;
  }

  private static Resolved resolve(Map<String, Object> facts, String path) {
    Object current = facts;
    String field = null;
    for (String part : path.split("\\.")) {
      if (current instanceof Map<?, ?> map) {
        current = map.get(part);
      } else if (current instanceof List<?> list && part.matches("\\d+") && Integer.parseInt(part) < list.size()) {
        current = list.get(Integer.parseInt(part));
      } else {
        return null;
      }
      field = part;
    }
    // 숫자나 날짜·이름 같은 하나의 값만 넣을 수 있다 (표 전체는 안 됨)
    return current instanceof Map || current instanceof List ? null : new Resolved(field, current);
  }

  static String format(String field, Object value) {
    if (value instanceof Number number) {
      double v = number.doubleValue();
      if (field.endsWith("Krw")) {
        return new DecimalFormat("#,##0").format(Math.round(v)) + "원";
      }
      if (field.endsWith("Hours") || field.equals("playHours") || field.equals("hoursLast2Weeks")) {
        return new DecimalFormat("#,##0.#").format(v) + "시간";
      }
      if (field.endsWith("Minutes")) {
        return new DecimalFormat("#,##0").format(Math.round(v)) + "분";
      }
      if (field.endsWith("Percent") || field.equals("percent") || field.startsWith("percentOf") || field.endsWith("PercentGap")) {
        return new DecimalFormat("#,##0").format(Math.round(v)) + "%";
      }
      if (field.equals("thisYear") || field.equals("lastYear") || field.equals("year")) {
        return Math.round(v) + "년";
      }
      return new DecimalFormat("#,##0.#").format(v);
    }
    String text = String.valueOf(value);
    Matcher date = DATE.matcher(text);
    if (date.matches()) {
      return Integer.parseInt(date.group(1)) + "년 " + Integer.parseInt(date.group(2)) + "월 " + Integer.parseInt(date.group(3)) + "일";
    }
    Matcher yearMonth = YEAR_MONTH.matcher(text);
    if (yearMonth.matches()) {
      return Integer.parseInt(yearMonth.group(1)) + "년 " + Integer.parseInt(yearMonth.group(2)) + "월";
    }
    return text;
  }

  // 근거 보기용 사람 말 이름: "2025년(작년) 지출", "Dead Cells 시간당 비용", "이번 달 남은 예산"
  static String describe(Map<String, Object> facts, String path) {
    String[] parts = path.split("\\.");
    List<String> words = new ArrayList<>();
    Object current = facts;
    for (int i = 0; i < parts.length; i++) {
      String part = parts[i];
      Object next = current instanceof Map<?, ?> map ? map.get(part)
          : current instanceof List<?> list && part.matches("\\d+") ? list.get(Integer.parseInt(part)) : null;
      boolean leaf = i == parts.length - 1;
      if (leaf) {
        words.add(FIELD_LABELS.getOrDefault(part, part));
      } else if (i > 0 && "spendingByYear".equals(parts[i - 1]) && next instanceof Map<?, ?> year) {
        words.add(part + "년(" + String.valueOf(year.get("relative")).replace("(", ", ").replace(")", "") + ")");
      } else if (i > 0 && "spendingByMonthLast12".equals(parts[i - 1])) {
        words.add(part.replace("-", "년 ") + "월");
      } else if (next instanceof Map<?, ?> row && (row.get("name") != null || row.get("genre") != null)) {
        words.add(String.valueOf(row.get("name") != null ? row.get("name") : row.get("genre")));
      } else if (SECTION_LABELS.containsKey(part)) {
        words.add(SECTION_LABELS.get(part));
      }
      current = next;
    }
    return String.join(" ", words.stream().filter(w -> !w.isBlank()).toList());
  }
}
