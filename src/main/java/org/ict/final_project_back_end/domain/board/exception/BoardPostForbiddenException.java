package org.ict.final_project_back_end.domain.board.exception;
import org.ict.final_project_back_end.global.exception.BusinessException;
import org.springframework.http.HttpStatus;
public class BoardPostForbiddenException extends BusinessException {
  public BoardPostForbiddenException() { super(HttpStatus.FORBIDDEN, "작성자만 수정하거나 삭제할 수 있습니다."); }
}