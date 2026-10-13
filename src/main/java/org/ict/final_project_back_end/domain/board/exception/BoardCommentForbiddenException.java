package org.ict.final_project_back_end.domain.board.exception;
import org.ict.final_project_back_end.global.exception.BusinessException;
import org.springframework.http.HttpStatus;
public class BoardCommentForbiddenException extends BusinessException {
  public BoardCommentForbiddenException() { super(HttpStatus.FORBIDDEN, "BOARD_COMMENT_FORBIDDEN", "댓글 작성자만 수정하거나 삭제할 수 있습니다."); }
}
