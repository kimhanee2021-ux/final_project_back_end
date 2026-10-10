package org.ict.final_project_back_end.domain.notice.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record NoticePageResponse(List<NoticeSummaryResponse> content, int page, int size,
    long totalElements, int totalPages, boolean first, boolean last) {
  public static NoticePageResponse from(Page<NoticeSummaryResponse> result) {
    return new NoticePageResponse(List.copyOf(result.getContent()), result.getNumber(), result.getSize(),
        result.getTotalElements(), result.getTotalPages(), result.isFirst(), result.isLast());
  }
}
