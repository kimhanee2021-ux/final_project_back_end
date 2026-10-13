package org.ict.final_project_back_end.global.exception;

import org.springframework.http.HttpStatus;

// 비즈니스 규칙 위반을 나타내는 예외의 공통 베이스. HTTP 상태코드와 에러 code를 함께 들고 다녀서
// GlobalExceptionHandler가 예외 종류별 분기 없이 일관되게 응답을 만들 수 있게 한다.
// code는 프론트가 문구 대신 분기에 쓰는 값이므로, 한 번 정하면 바꾸지 않는다.
public abstract class BusinessException extends RuntimeException {

  private final HttpStatus status;
  private final String code;

  protected BusinessException(HttpStatus status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public HttpStatus getStatus() {
    return status;
  }

  public String getCode() {
    return code;
  }
}
