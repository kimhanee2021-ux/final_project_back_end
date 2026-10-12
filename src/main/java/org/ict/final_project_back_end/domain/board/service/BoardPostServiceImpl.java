package org.ict.final_project_back_end.domain.board.service;

import org.ict.final_project_back_end.domain.board.dto.BoardPostCreateRequest;
import org.ict.final_project_back_end.domain.board.dto.BoardPostCreateResponse;
import org.ict.final_project_back_end.domain.board.dto.BoardPostDetailResponse;
import org.ict.final_project_back_end.domain.board.entity.BoardPost;
import org.ict.final_project_back_end.domain.board.exception.BoardPostNotFoundException;
import org.ict.final_project_back_end.domain.board.repository.BoardPostRepository;
import org.ict.final_project_back_end.domain.user.entity.User;
import org.ict.final_project_back_end.domain.user.repository.UserRepository;
import org.ict.final_project_back_end.global.exception.UserNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.ict.final_project_back_end.domain.board.dto.BoardPostPageResponse;
import org.ict.final_project_back_end.domain.board.dto.BoardPostSummaryResponse;
import org.ict.final_project_back_end.domain.board.repository.BoardPostSpecifications;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BoardPostServiceImpl implements BoardPostService {
  private final BoardPostRepository posts;
  private final UserRepository users;
  private final BoardLikeService likes;
  private final BoardPostAccessGuard accessGuard;

  public BoardPostServiceImpl(BoardPostRepository posts, UserRepository users, BoardLikeService likes,
      BoardPostAccessGuard accessGuard) {
    this.posts = posts;
    this.users = users;
    this.likes = likes;
    this.accessGuard = accessGuard;
  }

  @Override
  @Transactional
  public BoardPostCreateResponse createPost(Long authenticatedUserId, BoardPostCreateRequest request) {
    accessGuard.requireLogin(authenticatedUserId);
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

  @Override
  public BoardPostPageResponse listPosts(int page, int size, String category, String keyword) {
    var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    var result = posts.findAll(BoardPostSpecifications.matching(category, keyword), pageable)
        .map(BoardPostSummaryResponse::from);
    return BoardPostPageResponse.from(result);
  }

  @Override
  @Transactional
  public BoardPostDetailResponse updatePost(Long userId, Long postId,
      org.ict.final_project_back_end.domain.board.dto.BoardPostUpdateRequest request) {
    BoardPost post = accessGuard.ownedPostForUpdate(userId, postId);
    post.update(request.category(), request.appId(), request.tag(), request.title(),
        request.content(), request.isAnonymous(), request.expiresAt());
    posts.flush();
    return BoardPostDetailResponse.from(post);
  }

  @Override
  @Transactional
  public void deletePost(Long userId, Long postId) {
    BoardPost post = accessGuard.ownedPostForUpdate(userId, postId);
    if (posts.countComments(postId) > 0 || posts.countImages(postId) > 0) {
      throw new org.ict.final_project_back_end.domain.board.exception.BoardPostConflictException();
    }
    likes.removeAllForPost(postId);
    posts.delete(post);
    posts.flush();
  }
}
