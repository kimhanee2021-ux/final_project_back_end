package org.ict.final_project_back_end.global.exception;

import org.springframework.http.HttpStatus;

public class DuplicateNicknameException extends BusinessException {

  public DuplicateNicknameException() {
    super(HttpStatus.CONFLICT, "DUPLICATE_NICKNAME", "이미 사용 중인 닉네임입니다");
  }
}
