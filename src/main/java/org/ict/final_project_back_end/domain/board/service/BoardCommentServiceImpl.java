package org.ict.final_project_back_end.domain.board.service;
import org.ict.final_project_back_end.domain.board.dto.*;
import org.ict.final_project_back_end.domain.board.entity.BoardComment;
import org.ict.final_project_back_end.domain.board.exception.*;
import org.ict.final_project_back_end.domain.board.repository.BoardCommentRepository;
import org.ict.final_project_back_end.domain.board.repository.BoardPostRepository;
import org.ict.final_project_back_end.domain.user.repository.UserRepository;
import org.ict.final_project_back_end.global.exception.InvalidCredentialsException;
import org.ict.final_project_back_end.global.exception.UserNotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BoardCommentServiceImpl implements BoardCommentService {
  private final BoardCommentRepository comments;
  private final BoardPostRepository posts;
  private final UserRepository users;
  public BoardCommentServiceImpl(BoardCommentRepository comments, BoardPostRepository posts, UserRepository users) {
    this.comments = comments;
    this.posts = posts;
    this.users = users;
  }
  @Override
  @Transactional
  public BoardCommentResponse create(Long userId, Long postId, BoardCommentCreateRequest request) {
    requireLogin(userId);
    var post = posts.findById(postId).orElseThrow(BoardPostNotFoundException::new);
    var author = users.findById(userId).orElseThrow(UserNotFoundException::new);
    BoardComment parent = null;
    if (request.parentId() != null) {
      parent = comments.findByIdAndPostId(request.parentId(), postId)
          .orElseThrow(BoardCommentNotFoundException::new);
      if (parent.getParent() != null) throw new BoardReplyDepthException();
    }
    var comment = BoardComment.builder().post(post).author(author).parent(parent).content(request.content()).build();
    return BoardCommentResponse.from(comments.save(comment));
  }
  @Override
  public BoardCommentPageResponse list(Long postId, int page, int size) {
    if (!posts.existsById(postId)) throw new BoardPostNotFoundException();
    var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt", "id"));
    return BoardCommentPageResponse.from(comments.findByPostId(postId, pageable).map(BoardCommentResponse::from));
  }
  @Override
  @Transactional
  public BoardCommentResponse update(Long userId, Long postId, Long commentId, BoardCommentRequest request) {
    var comment = ownedComment(userId, postId, commentId);
    comment.updateContent(request.content());
    comments.flush();
    return BoardCommentResponse.from(comment);
  }
  @Override
  @Transactional
  public void delete(Long userId, Long postId, Long commentId) {
    var comment = ownedComment(userId, postId, commentId);
    if (comments.existsByParentId(commentId)) throw new BoardCommentConflictException();
    comments.delete(comment);
    comments.flush();
  }
  private BoardComment ownedComment(Long userId, Long postId, Long commentId) {
    requireLogin(userId);
    var comment = comments.findByIdAndPostId(commentId, postId).orElseThrow(BoardCommentNotFoundException::new);
    if (!comment.getAuthor().getId().equals(userId)) throw new BoardCommentForbiddenException();
    return comment;
  }
  private void requireLogin(Long userId) {
    if (userId == null || userId <= 0) throw new InvalidCredentialsException("로그인이 필요합니다.");
  }
}
