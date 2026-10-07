package org.ict.final_project_back_end.domain.board.service;
import org.ict.final_project_back_end.domain.board.dto.BoardLikeResponse;
public interface BoardLikeService {
  BoardLikeResponse status(Long userId, Long postId);
  BoardLikeResponse like(Long userId, Long postId);
  BoardLikeResponse cancel(Long userId, Long postId);
}
