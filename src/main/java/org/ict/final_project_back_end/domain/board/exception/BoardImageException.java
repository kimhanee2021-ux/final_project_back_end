package org.ict.final_project_back_end.domain.board.exception;
import org.ict.final_project_back_end.global.exception.BusinessException;
import org.springframework.http.HttpStatus;
public class BoardImageException extends BusinessException {
  public BoardImageException(HttpStatus status, String code, String message) { super(status, code, message); }
}
