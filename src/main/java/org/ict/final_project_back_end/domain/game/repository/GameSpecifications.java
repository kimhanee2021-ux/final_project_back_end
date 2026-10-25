package org.ict.final_project_back_end.domain.game.repository;

import java.util.Locale;
import org.ict.final_project_back_end.domain.game.entity.Game;
import org.springframework.data.jpa.domain.Specification;

public final class GameSpecifications {
  private GameSpecifications() { }

  // 장르 부분 일치 (대소문자 무시). 장르가 비어 있으면 조건 없이 전체
  public static Specification<Game> genreContains(String genre) {
    return (root, query, builder) -> genre == null || genre.isBlank()
        ? builder.conjunction()
        : builder.like(builder.lower(root.get("genre")), containsPattern(genre), '\\');
  }

  private static String containsPattern(String value) {
    String literal = value.strip().toLowerCase(Locale.ROOT)
        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    return "%" + literal + "%";
  }
}
