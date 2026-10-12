package org.ict.final_project_back_end.domain.board.dto;

import java.time.LocalDateTime;
import org.ict.final_project_back_end.domain.board.entity.BoardPost;

public record BoardPostSummaryResponse(Long id, String category, Long appId, String tag,
    String title, String imageUrl, boolean isAnonymous, int viewCount, Long authorId, LocalDateTime createdAt) {
  public static BoardPostSummaryResponse from(BoardPost post) {
    boolean anonymous = Boolean.TRUE.equals(post.getAnonymous());
    return new BoardPostSummaryResponse(post.getId(), post.getCategory(), post.getAppId(), post.getTag(),
        post.getTitle(), post.getImageUrl(), anonymous, post.getViewCount(),
        anonymous ? null : post.getAuthor().getId(), post.getCreatedAt());
  }
}
