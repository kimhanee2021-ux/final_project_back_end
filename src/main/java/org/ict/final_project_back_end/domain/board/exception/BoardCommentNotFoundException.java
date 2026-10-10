package org.ict.final_project_back_end.domain.board.exception;
import org.ict.final_project_back_end.global.exception.BusinessException;
import org.springframework.http.HttpStatus;
public class BoardCommentNotFoundException extends BusinessException {
  public BoardCommentNotFoundException() { super(HttpStatus.NOT_FOUND, "BOARD_COMMENT_NOT_FOUND", "댓글을 찾을 수 없습니다."); }
}
