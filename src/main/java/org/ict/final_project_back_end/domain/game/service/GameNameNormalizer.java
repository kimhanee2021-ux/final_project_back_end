package org.ict.final_project_back_end.domain.game.service;

import java.text.Normalizer;
import java.util.Locale;

public final class GameNameNormalizer {
  private GameNameNormalizer() {}
  public static String normalize(String name) {
    if (name == null) return "";
    return Normalizer.normalize(name, Normalizer.Form.NFKC)
        .replaceAll("[\\p{Z}\\s]+", " ").strip().toLowerCase(Locale.ROOT);
  }
  public static String query(String name) {
    if (name == null || name.length() > 255) throw GameSearchException.invalid("게임명은 1~255자로 입력해 주세요.");
    String normalized = normalize(name);
    if (normalized.isBlank() || normalized.length() > 255 || normalized.codePoints().anyMatch(Character::isISOControl))
      throw GameSearchException.invalid("검색할 게임명을 입력해 주세요.");
    return normalized;
  }
  public static String escapeLike(String value) {
    return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
  }
}
