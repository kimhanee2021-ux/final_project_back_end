package org.ict.final_project_back_end.domain.board.dto;
import java.time.LocalDateTime;
import org.ict.final_project_back_end.domain.board.entity.BoardComment;
public record BoardCommentResponse(Long id, Long postId, Long parentId, String content,
    Long authorId, String authorNickname, boolean accepted, LocalDateTime createdAt) {
  public static BoardCommentResponse from(BoardComment comment) {
    return new BoardCommentResponse(comment.getId(), comment.getPost().getId(),
        comment.getParent() == null ? null : comment.getParent().getId(), comment.getContent(),
        comment.getAuthor().getId(), comment.getAuthor().getNickname(),
        Boolean.TRUE.equals(comment.getAccepted()), comment.getCreatedAt());
  }
}
