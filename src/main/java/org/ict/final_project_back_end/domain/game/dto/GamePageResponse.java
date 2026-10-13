package org.ict.final_project_back_end.domain.game.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record GamePageResponse(List<GameSummaryResponse> content, int page, int size,
    long totalElements, int totalPages, boolean first, boolean last) {
  public static GamePageResponse from(Page<GameSummaryResponse> page) {
    return new GamePageResponse(List.copyOf(page.getContent()), page.getNumber(), page.getSize(),
        page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
  }
}
