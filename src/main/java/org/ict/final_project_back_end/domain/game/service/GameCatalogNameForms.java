package org.ict.final_project_back_end.domain.game.service;

import java.util.*;

/** Name-derived forms apply to every catalog row; abbreviations may collide. */
public final class GameCatalogNameForms {
  private GameCatalogNameForms() {}
  public static String compact(String text) {
    return GameNameNormalizer.normalize(text).replaceAll("[\\p{Z}\\s\\p{P}]+", "");
  }
  public static List<String> abbreviations(String name) {
    String clean = GameNameNormalizer.normalize(name).replace("'", "").replace("’", "");
    var forms = new LinkedHashSet<>(basicAbbreviations(clean));
    String core = clean.replaceFirst("\\b(?:complete|enhanced|definitive|legacy|remastered|remaster|edition|playtest|demo|ultimate|deluxe|anniversary)\\b.*$", "")
        .replaceFirst("\\bthe\\s*$", "").replaceFirst("[^a-z0-9]+$", "").strip();
    if (!core.isEmpty() && !core.equals(clean)) forms.addAll(basicAbbreviations(core));
    return List.copyOf(forms);
  }
  private static List<String> basicAbbreviations(String clean) {
    String[] words = clean.split("[^\\p{L}\\p{N}]+");
    if (words.length < 2 || !clean.matches("[a-z0-9\\p{P}\\s]+")) return List.of();
    StringBuilder all = new StringBuilder(), base = new StringBuilder();
    int wordCount = 0;
    for (String word : words) {
      if (word.isBlank()) continue;
      boolean version = word.matches("[0-9]+|ii|iii|iv|v|vi|vii|viii|ix|x");
      String part = version ? word : word.substring(0, 1);
      all.append(part);
      if (!version) { base.append(part); wordCount++; }
    }
    if (wordCount < 2 || all.length() > 12) return List.of();
    return new LinkedHashSet<>(List.of(all.toString(), base.toString())).stream().toList();
  }
  public static boolean sameNumbers(String a, String b) {
    return numbers(a).equals(numbers(b));
  }
  private static List<String> numbers(String text) {
    String normalized = GameNameNormalizer.normalize(text);
    var romans = Map.of("ii", "2", "iii", "3", "iv", "4", "v", "5", "vi", "6", "vii", "7", "viii", "8", "ix", "9", "x", "10");
    for (var entry : romans.entrySet()) normalized = normalized.replaceAll("\\b" + entry.getKey() + "\\b", entry.getValue());
    return java.util.regex.Pattern.compile("[0-9]+").matcher(normalized).results().map(m -> m.group()).toList();
  }
  public static boolean near(String a, String b) {
    if (a.length() < 3 || b.length() < 3 || !sameNumbers(a, b) || Math.abs(a.length() - b.length()) > 1) return false;
    int i = 0, j = 0, edits = 0;
    while (i < a.length() && j < b.length()) {
      if (a.charAt(i) == b.charAt(j)) { i++; j++; continue; }
      if (++edits > 1) return false;
      if (a.length() >= b.length()) i++;
      if (b.length() >= a.length()) j++;
    }
    return edits + a.length() - i + b.length() - j == 1;
  }
}
