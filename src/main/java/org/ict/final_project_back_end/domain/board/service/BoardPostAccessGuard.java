package org.ict.final_project_back_end.domain.board.service;

import org.ict.final_project_back_end.domain.board.entity.BoardPost;
import org.ict.final_project_back_end.domain.board.exception.BoardPostForbiddenException;
import org.ict.final_project_back_end.domain.board.exception.BoardPostNotFoundException;
import org.ict.final_project_back_end.domain.board.repository.BoardPostRepository;
import org.ict.final_project_back_end.global.exception.AuthenticationRequiredException;
import org.springframework.stereotype.Component;

// 게시글 소유자 검증 로직. BoardPostServiceImpl/BoardImageServiceImpl 등 board 패키지 내부에서만 공유한다.
@Component
class BoardPostAccessGuard {
  private final BoardPostRepository posts;

  BoardPostAccessGuard(BoardPostRepository posts) {
    this.posts = posts;
  }

  void requireLogin(Long userId) {
    if (userId == null || userId <= 0) {
      throw new AuthenticationRequiredException();
    }
  }

  BoardPost ownedPostForUpdate(Long userId, Long postId) {
    requireLogin(userId);
    BoardPost post = posts.findByIdForUpdate(postId).orElseThrow(BoardPostNotFoundException::new);
    if (!post.getAuthor().getId().equals(userId)) {
      throw new BoardPostForbiddenException();
    }
    return post;
  }
}
