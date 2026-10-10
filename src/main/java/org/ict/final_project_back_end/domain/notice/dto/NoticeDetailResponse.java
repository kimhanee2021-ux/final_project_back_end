package org.ict.final_project_back_end.domain.notice.dto;

import java.time.LocalDateTime;
import org.ict.final_project_back_end.domain.notice.entity.Notice;

public record NoticeDetailResponse(Long id, String title, String content, Long authorId,
    LocalDateTime createdAt, LocalDateTime updatedAt) {
  public static NoticeDetailResponse from(Notice notice) {
    return new NoticeDetailResponse(notice.getId(), notice.getTitle(), notice.getContent(),
        notice.getAuthor() == null ? null : notice.getAuthor().getId(), notice.getCreatedAt(), notice.getUpdatedAt());
  }
}
