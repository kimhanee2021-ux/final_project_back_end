package org.ict.final_project_back_end.domain.board.service;
import org.ict.final_project_back_end.domain.board.dto.BoardLikeResponse;
import org.ict.final_project_back_end.domain.board.entity.ContentInteraction;
import org.ict.final_project_back_end.domain.board.exception.BoardPostNotFoundException;
import org.ict.final_project_back_end.domain.board.repository.BoardPostRepository;
import org.ict.final_project_back_end.domain.board.repository.ContentInteractionRepository;
import org.ict.final_project_back_end.domain.user.repository.UserRepository;
import org.ict.final_project_back_end.global.exception.UserNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BoardLikeServiceImpl implements BoardLikeService {
  private static final String TARGET_TYPE = "BOARD_POST";
  private static final String LIKE = "LIKE";
  private final BoardPostRepository posts;
  private final ContentInteractionRepository interactions;
  private final UserRepository users;
  private final BoardPostAccessGuard accessGuard;
  public BoardLikeServiceImpl(BoardPostRepository posts, ContentInteractionRepository interactions, UserRepository users,
      BoardPostAccessGuard accessGuard) {
    this.posts = posts;
    this.interactions = interactions;
    this.users = users;
    this.accessGuard = accessGuard;
  }
  @Override
  public BoardLikeResponse status(Long userId, Long postId) {
    accessGuard.requireLogin(userId);
    if (!posts.existsById(postId)) throw new BoardPostNotFoundException();
    return response(userId, postId);
  }
  @Override
  @Transactional
  public BoardLikeResponse like(Long userId, Long postId) {
    accessGuard.requireLogin(userId);
    posts.findByIdForUpdate(postId).orElseThrow(BoardPostNotFoundException::new);
    var user = users.findById(userId).orElseThrow(UserNotFoundException::new);
    if (interactions.findByTargetTypeAndTargetIdAndUserIdAndInteractionType(TARGET_TYPE, postId, userId, LIKE).isEmpty()) {
      interactions.saveAndFlush(ContentInteraction.builder().targetType(TARGET_TYPE).targetId(postId)
          .user(user).interactionType(LIKE).build());
    }
    return response(userId, postId);
  }
  @Override
  @Transactional
  public BoardLikeResponse cancel(Long userId, Long postId) {
    accessGuard.requireLogin(userId);
    posts.findByIdForUpdate(postId).orElseThrow(BoardPostNotFoundException::new);
    interactions.findByTargetTypeAndTargetIdAndUserIdAndInteractionType(TARGET_TYPE, postId, userId, LIKE)
        .ifPresent(interactions::delete);
    interactions.flush();
    return response(userId, postId);
  }
  private BoardLikeResponse response(Long userId, Long postId) {
    long count = interactions.countByTargetTypeAndTargetIdAndInteractionType(TARGET_TYPE, postId, LIKE);
    boolean liked = interactions.findByTargetTypeAndTargetIdAndUserIdAndInteractionType(TARGET_TYPE, postId, userId, LIKE).isPresent();
    return new BoardLikeResponse(postId, count, liked);
  }
  @Override
  @Transactional
  public void removeAllForPost(Long postId) {
    interactions.deleteByTargetTypeAndTargetId(TARGET_TYPE, postId);
  }
}
