package org.ict.final_project_back_end.domain.board.exception;

import org.ict.final_project_back_end.global.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class BoardPostNotFoundException extends BusinessException {
  public BoardPostNotFoundException() {
    super(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다.");
  }
}
