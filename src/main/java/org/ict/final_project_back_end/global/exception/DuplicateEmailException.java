package org.ict.final_project_back_end.global.exception;

import org.springframework.http.HttpStatus;

public class DuplicateEmailException extends BusinessException {

  public DuplicateEmailException() {
    super(HttpStatus.CONFLICT, "DUPLICATE_EMAIL", "이미 가입된 이메일입니다");
  }
}
