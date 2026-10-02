package org.ict.final_project_back_end.global.exception;

import org.springframework.http.HttpStatus;

public class InvalidCredentialsException extends BusinessException {

  public InvalidCredentialsException(String message) {
    super(HttpStatus.UNAUTHORIZED, message);
  }
}
