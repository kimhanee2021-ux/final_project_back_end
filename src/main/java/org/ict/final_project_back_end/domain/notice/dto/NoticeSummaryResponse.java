package org.ict.final_project_back_end.domain.notice.dto;

import java.time.LocalDateTime;
import org.ict.final_project_back_end.domain.notice.entity.Notice;

public record NoticeSummaryResponse(Long id, String title, LocalDateTime createdAt) {
  public static NoticeSummaryResponse from(Notice notice) {
    return new NoticeSummaryResponse(notice.getId(), notice.getTitle(), notice.getCreatedAt());
  }
}
