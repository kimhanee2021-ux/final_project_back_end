package org.ict.final_project_back_end.domain.game.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

// Page 객체를 그대로 내보내면 JSON 구조가 Spring 버전에 따라 바뀌므로 필요한 필드만 담아 반환한다
public record GamePageResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {

  public static <E, T> GamePageResponse<T> from(Page<E> page, Function<E, T> mapper) {
    return new GamePageResponse<>(
        page.getContent().stream().map(mapper).toList(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
  }
}
