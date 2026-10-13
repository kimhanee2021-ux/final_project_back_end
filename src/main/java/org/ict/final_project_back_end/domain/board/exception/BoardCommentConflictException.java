package org.ict.final_project_back_end.domain.board.exception;
import org.ict.final_project_back_end.global.exception.BusinessException;
import org.springframework.http.HttpStatus;
public class BoardCommentConflictException extends BusinessException {
  public BoardCommentConflictException() { super(HttpStatus.CONFLICT, "BOARD_COMMENT_HAS_REPLIES", "답글이 있는 댓글은 아직 삭제할 수 없습니다."); }
}
