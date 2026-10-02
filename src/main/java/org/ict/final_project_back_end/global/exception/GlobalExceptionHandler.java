package org.ict.final_project_back_end.global.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// 서비스에서 던진 BusinessException을 한 곳에서 HTTP 응답으로 변환한다.
// 덕분에 서비스는 HTTP를 몰라도 되고(순수 비즈니스 로직), 컨트롤러도 에러 응답 포맷을 신경 쓰지 않는다.
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
    return ResponseEntity.status(e.getStatus()).body(new ErrorResponse(e.getMessage()));
  }
}
