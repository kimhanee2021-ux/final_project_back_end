package org.ict.final_project_back_end.domain.board.service;
import org.ict.final_project_back_end.domain.board.dto.*;
public interface BoardCommentService {
  BoardCommentResponse create(Long userId, Long postId, BoardCommentCreateRequest request);
  BoardCommentPageResponse list(Long postId, int page, int size);
  BoardCommentResponse update(Long userId, Long postId, Long commentId, BoardCommentRequest request);
  void delete(Long userId, Long postId, Long commentId);
}
