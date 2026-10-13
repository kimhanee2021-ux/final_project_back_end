package org.ict.final_project_back_end.global.exception;

import org.springframework.http.HttpStatus;

// 로그인(인증 정보) 자체가 없는 상태에서 로그인이 필요한 기능을 호출한 경우.
// 자격증명이 틀린 InvalidCredentialsException과 구분해서, 프론트가 자동 로그아웃 여부를 판단할 수 있게 한다.
public class AuthenticationRequiredException extends BusinessException {

  public AuthenticationRequiredException() {
    super(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "로그인이 필요합니다.");
  }
}
