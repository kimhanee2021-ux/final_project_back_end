package org.ict.final_project_back_end.domain.game.repository;

import java.util.ArrayList;
import java.util.Locale;
import jakarta.persistence.criteria.Predicate;
import org.ict.final_project_back_end.domain.game.entity.Game;
import org.springframework.data.jpa.domain.Specification;

public final class GameSpecifications {
  private GameSpecifications() { }

  public static Specification<Game> matching(String genre, String name) {
    return (root, query, builder) -> {
      var predicates = new ArrayList<Predicate>();
      if (genre != null && !genre.isBlank()) {
        predicates.add(builder.like(builder.lower(root.get("genre")), containsPattern(genre), '\\'));
      }
      if (name != null && !name.isBlank()) {
        predicates.add(builder.like(builder.lower(root.get("name")), containsPattern(name), '\\'));
      }
      return builder.and(predicates.toArray(Predicate[]::new));
    };
  }

  private static String containsPattern(String value) {
    String literal = value.strip().toLowerCase(Locale.ROOT)
        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    return "%" + literal + "%";
  }
}
