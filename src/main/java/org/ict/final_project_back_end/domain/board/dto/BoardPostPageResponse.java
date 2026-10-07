package org.ict.final_project_back_end.domain.board.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record BoardPostPageResponse(List<BoardPostSummaryResponse> content, int page, int size,
    long totalElements, int totalPages, boolean first, boolean last) {
  public static BoardPostPageResponse from(Page<BoardPostSummaryResponse> page) {
    return new BoardPostPageResponse(List.copyOf(page.getContent()), page.getNumber(), page.getSize(),
        page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
  }
}
