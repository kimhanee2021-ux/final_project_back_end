package org.ict.final_project_back_end.domain.gamenews.service;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;
import org.ict.final_project_back_end.domain.gamenews.dto.NewsVoiceIntent.Slots;

public final class NewsVoiceRules {
  private NewsVoiceRules() {}
  public static final Pattern EXPLICIT_ID = Pattern.compile("^(?:스팀\\s*)?(?:게임\\s*(?:아이디|ID|번호)|앱\\s*(?:아이디|ID)|app\\s*id)\\s*[:：]?\\s*([0-9]+)", Pattern.CASE_INSENSITIVE);
  public static final Pattern ORDINAL = Pattern.compile("^(?:후보\\s*)?([0-9]{1,3})\\s*번$");
  private static final Map<String, String> WORDS = new LinkedHashMap<>();
  private static final Pattern CORRECTION = Pattern.compile("(?:^|[\\s,，])(?:아니(?:요)?|그거\\s*말고|대신)(?=$|[\\s,，])|말고");
  public static boolean isCorrection(String text) { return CORRECTION.matcher(clean(text)).find(); }
  public static boolean incompleteCorrection(String text) {
    return clean(text).matches(".*(?:^|[\\s,，])(?:아니|아니요|그거\\s*말고|대신)$") || clean(text).endsWith("말고");
  }
  static {
    WORDS.put("NOTICE", "공지(?:사항)?|운영\\s*(?:안내|소식)");
    WORDS.put("PATCH", "업데이트|패치|밸런스|업뎃|(?i:\\b(?:updates?|patch(?:es)?|balance)\\b)");
    WORDS.put("EVENT", "이벤트|행사|(?i:\\bevents?\\b)");
    WORDS.put("MAINTENANCE", "점검|장애");
    WORDS.put("DEV_NEWS", "개발\\s*(?:소식|뉴스|일지)|개발");
    WORDS.put("OTHER", "기타|그\\s*밖의\\s*소식");
  }
  public static String clean(String text) { return Normalizer.normalize(text, Normalizer.Form.NFKC).strip().replaceAll("[.!?。]+$", "").strip(); }
  public static Slots command(String command) { return new Slots(null, null, false, false, false, command); }
  /** Null means the bounded AI extractor must interpret this sentence. */
  public static Slots extract(String transcript) {
    String text = clean(transcript);
    var correction = CORRECTION.matcher(text);
    if (correction.find()) {
      int start = correction.start(), end = correction.end();
      while (correction.find()) { start = correction.start(); end = correction.end(); }
      String before = text.substring(0, start).replaceAll("[,，]+$", "").strip();
      String after = text.substring(end).replaceAll("^[\\s,，]+", "").strip();
      if (after.isEmpty() || isCorrection(before)) return null;
      Slots replacement = simple(after);
      if (replacement == null || !replacement.command().equals("SEARCH")) return null;
      if (before.isEmpty()) return replacement;
      Slots original = simple(before);
      if (original == null || !original.command().equals("SEARCH") || original.multipleCategories()
          || (replacement.useCurrentGame() && original.gameQuery() != null)) return null;
      boolean category = replacement.category() != null || replacement.allCategories() || replacement.multipleCategories();
      return new Slots(replacement.gameQuery() != null ? replacement.gameQuery() : original.gameQuery(),
          category ? replacement.category() : original.category(), category ? replacement.allCategories() : original.allCategories(),
          replacement.multipleCategories(), replacement.useCurrentGame(), "SEARCH");
    }
    return simple(text);
  }
  private static Slots simple(String text) {
    if (text.matches("^(?:다시\\s*(?:읽어|들려|재생)(?:\\s*줘|\\s*주세요)?|다시\\s*듣기)$")) return command("REPLAY");
    if (text.matches("^(?:처음부터|초기화)(?:\\s*해\\s*(?:줘|주세요))?$")) return command("RESET");
    if (text.matches("^(?:다른\\s*게임|게임\\s*(?:바꾸기|바꿔\\s*줘|변경))$")) return command("CHANGE_GAME");
    if (text.matches("^(?:중지|그만|멈춰|정지)(?:\\s*(?:해\\s*)?(?:줘|주세요))?$")) return command("STOP");
    if (text.matches("^(?:안녕|안녕하세요|오늘 날씨|고마워)$")) return command("UNSUPPORTED");
    Set<String> categories = new LinkedHashSet<>();
    String rest = text;
    for (var entry : WORDS.entrySet()) {
      if (Pattern.compile(entry.getValue()).matcher(text).find()) categories.add(entry.getKey());
      rest = rest.replaceAll(entry.getValue(), " ");
    }
    String allPattern = "전체(?:\\s*(?:소식|뉴스))?|모든\\s*(?:소식|뉴스)|(?i:\\ball\\s+news\\b)";
    boolean all = Pattern.compile(allPattern).matcher(text).find();
    rest = rest.replaceAll(allPattern, " ")
        .replaceAll("(?i)^(?:please\\s+)?(?:show|tell|give)\\s+me\\s+", " ")
        .replaceAll("(?i)\\b(?:news|please)\\b", " ")
        .replaceAll("(?:소식|뉴스|브리핑)(?:을|를|이|가|은|는)?", " ")
        .replaceAll("(?:알려|읽어|들려|보여|찾아|검색해|말해)(?:\\s*줘|\\s*주세요|\\s*줄래)?$", " ")
        .replaceAll("\\s+", " ").strip();
    boolean multiple = categories.size() + (all ? 1 : 0) > 1;
    rest = rest.replaceAll("(?:^|\\s)(?:랑|하고|와|과)(?=\\s|$)", " ").strip();
    if (!categories.isEmpty() || all) rest = rest.replaceAll("(?:의|에서|에 대한)$", "").strip();
    boolean current = rest.matches("(?:이|그|현재|선택한|현재 선택한)\\s*게임");
    if (current) rest = "";
    if (rest.matches(".*(?:이번|요즘|내용|정리|뭐|무엇|어떻게|줄래|어요|주세요|알려|설명|좀|지금|날씨|무시|명령|그거|그걸|아까|방금|전에|대신|말고|아니|같은).*")) return null;
    if (rest.length() > 100 || (categories.isEmpty() && !all && rest.contains(" ") && rest.length() > 40)) return null;
    return new Slots(rest.isBlank() ? null : rest, categories.size() == 1 && !multiple ? categories.iterator().next() : null,
        all && !multiple, multiple, current, "SEARCH");
  }
}
