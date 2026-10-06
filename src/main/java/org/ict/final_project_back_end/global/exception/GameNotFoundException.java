package org.ict.final_project_back_end.global.exception;

import org.springframework.http.HttpStatus;

public class GameNotFoundException extends BusinessException {

  public GameNotFoundException() {
    super(HttpStatus.NOT_FOUND, "존재하지 않는 게임입니다");
  }
}
