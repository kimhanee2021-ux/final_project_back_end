package org.ict.final_project_back_end.global.exception;

import org.springframework.http.HttpStatus;

public class UserNotFoundException extends BusinessException {

  public UserNotFoundException() {
    super(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "존재하지 않는 회원입니다");
  }
}
