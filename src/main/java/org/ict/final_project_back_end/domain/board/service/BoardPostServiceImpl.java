package org.ict.final_project_back_end.domain.board.service;

import org.ict.final_project_back_end.domain.board.dto.BoardPostCreateRequest;
import org.ict.final_project_back_end.domain.board.dto.BoardPostCreateResponse;
import org.ict.final_project_back_end.domain.board.dto.BoardPostDetailResponse;
import org.ict.final_project_back_end.domain.board.entity.BoardPost;
import org.ict.final_project_back_end.domain.board.exception.BoardPostNotFoundException;
import org.ict.final_project_back_end.domain.board.repository.BoardPostRepository;
import org.ict.final_project_back_end.domain.user.entity.User;
import org.ict.final_project_back_end.domain.user.repository.UserRepository;
import org.ict.final_project_back_end.global.exception.InvalidCredentialsException;
import org.ict.final_project_back_end.global.exception.UserNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BoardPostServiceImpl implements BoardPostService {
  private final BoardPostRepository posts;
  private final UserRepository users;

  public BoardPostServiceImpl(BoardPostRepository posts, UserRepository users) {
    this.posts = posts;
    this.users = users;
  }

  @Override
  @Transactional
  public BoardPostCreateResponse createPost(Long authenticatedUserId, BoardPostCreateRequest request) {
    if (authenticatedUserId == null || authenticatedUserId <= 0) {
      throw new InvalidCredentialsException("로그인이 필요합니다.");
    }
    User author = users.findById(authenticatedUserId).orElseThrow(UserNotFoundException::new);
    BoardPost post = BoardPost.builder()
        .category(request.category()).appId(request.appId()).tag(request.tag())
        .title(request.title()).content(request.content()).anonymous(request.isAnonymous())
        .author(author).expiresAt(request.expiresAt()).build();
    return new BoardPostCreateResponse(posts.save(post).getId());
  }

  @Override
  public BoardPostDetailResponse findPost(Long postId) {
    BoardPost post = posts.findById(postId).orElseThrow(BoardPostNotFoundException::new);
    return BoardPostDetailResponse.from(post);
  }
}
