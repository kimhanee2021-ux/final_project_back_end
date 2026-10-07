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
  private final org.ict.final_project_back_end.domain.board.repository.ContentInteractionRepository interactions;

  public BoardPostServiceImpl(BoardPostRepository posts, UserRepository users, org.ict.final_project_back_end.domain.board.repository.ContentInteractionRepository interactions) {
    this.posts = posts;
    this.users = users;
    this.interactions = interactions;
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
    BoardPost post = ownedPost(userId, postId);
    post.update(request.category(), request.appId(), request.tag(), request.title(),
        request.content(), request.isAnonymous(), request.expiresAt());
    posts.flush();
    return BoardPostDetailResponse.from(post);
  }

  @Override
  @Transactional
  public void deletePost(Long userId, Long postId) {
    BoardPost post = ownedPost(userId, postId);
    if (posts.countComments(postId) > 0 || posts.countImages(postId) > 0) {
      throw new org.ict.final_project_back_end.domain.board.exception.BoardPostConflictException();
    }
    interactions.deleteByTargetTypeAndTargetId(BoardLikeServiceImpl.TARGET_TYPE, postId);
    posts.delete(post);
    posts.flush();
  }

  private BoardPost ownedPost(Long userId, Long postId) {
    if (userId == null || userId <= 0) {
      throw new InvalidCredentialsException("로그인이 필요합니다.");
    }
    BoardPost post = posts.findByIdForUpdate(postId).orElseThrow(BoardPostNotFoundException::new);
    if (!post.getAuthor().getId().equals(userId)) {
      throw new org.ict.final_project_back_end.domain.board.exception.BoardPostForbiddenException();
    }
    return post;
  }
}
