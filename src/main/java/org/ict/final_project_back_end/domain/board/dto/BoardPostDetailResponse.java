package org.ict.final_project_back_end.domain.board.dto;

import java.time.LocalDateTime;
import org.ict.final_project_back_end.domain.board.entity.BoardPost;

public record BoardPostDetailResponse(Long id, String category, Long appId, String tag,
    String title, String content, String imageUrl, boolean isAnonymous, int viewCount,
    Long authorId, LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime expiresAt) {
  public static BoardPostDetailResponse from(BoardPost post) {
    boolean anonymous = Boolean.TRUE.equals(post.getAnonymous());
    return new BoardPostDetailResponse(post.getId(), post.getCategory(), post.getAppId(), post.getTag(),
        post.getTitle(), post.getContent(), post.getImageUrl(), anonymous, post.getViewCount(),
        anonymous ? null : post.getAuthor().getId(), post.getCreatedAt(), post.getUpdatedAt(), post.getExpiresAt());
  }
}
