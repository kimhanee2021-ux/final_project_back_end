package org.ict.final_project_back_end.domain.board.repository;

import java.util.ArrayList;
import java.util.Locale;
import jakarta.persistence.criteria.Predicate;
import org.ict.final_project_back_end.domain.board.entity.BoardPost;
import org.springframework.data.jpa.domain.Specification;

public final class BoardPostSpecifications {
  private BoardPostSpecifications() { }

  public static Specification<BoardPost> matching(String category, String keyword) {
    return (root, query, builder) -> {
      var predicates = new ArrayList<Predicate>();
      if (category != null && !category.isBlank()) {
        predicates.add(builder.equal(root.get("category"), category.strip()));
      }
      if (keyword != null && !keyword.isBlank()) {
        String literal = keyword.strip().toLowerCase(Locale.ROOT)
            .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        String pattern = "%" + literal + "%";
        predicates.add(builder.or(builder.like(builder.lower(root.get("title")), pattern, '\\'),
            builder.like(builder.lower(root.get("content")), pattern, '\\')));
      }
      return builder.and(predicates.toArray(Predicate[]::new));
    };
  }
}
