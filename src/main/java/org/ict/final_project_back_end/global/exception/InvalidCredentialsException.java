package org.ict.final_project_back_end.global.exception;

import org.springframework.http.HttpStatus;

// 제출한 자격증명(이메일/비밀번호/토큰)이 틀린 경우. 로그인 자체가 안 된 경우는
// AuthenticationRequiredException을 쓴다.
public class InvalidCredentialsException extends BusinessException {

  public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";

  public InvalidCredentialsException(String code, String message) {
    super(HttpStatus.UNAUTHORIZED, code, message);
  }
}
