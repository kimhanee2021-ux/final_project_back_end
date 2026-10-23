package org.ict.final_project_back_end.domain.gamenews.dto;

import org.springframework.data.domain.Page;
import java.util.List;

public record GameNewsPageResponse(List<GameNewsSummaryResponse> content, int page, int size,
    long totalElements, int totalPages, boolean first, boolean last) {
  public static GameNewsPageResponse from(Page<GameNewsSummaryResponse> page) {
    return new GameNewsPageResponse(List.copyOf(page.getContent()), page.getNumber(), page.getSize(),
        page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
  }
}
