package org.ict.final_project_back_end.domain.board.service;
import org.ict.final_project_back_end.domain.board.dto.BoardLikeResponse;
public interface BoardLikeService {
  BoardLikeResponse status(Long userId, Long postId);
  BoardLikeResponse like(Long userId, Long postId);
  BoardLikeResponse cancel(Long userId, Long postId);

  // 게시글 삭제 시 함께 정리되어야 하는 좋아요 기록. 대상 타입/저장 방식은 이 도메인 내부 구현 사항이다.
  void removeAllForPost(Long postId);
}
