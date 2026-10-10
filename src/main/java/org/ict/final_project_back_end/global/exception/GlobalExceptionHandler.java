package org.ict.final_project_back_end.global.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

// 서비스에서 던진 BusinessException을 한 곳에서 HTTP 응답으로 변환한다.
// 덕분에 서비스는 HTTP를 몰라도 되고(순수 비즈니스 로직), 컨트롤러도 에러 응답 포맷을 신경 쓰지 않는다.
// 모든 에러 응답은 { "code": ..., "error": ... } 형태로 내려가며, 프론트는 code로만 분기한다.
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
    return ResponseEntity.status(e.getStatus()).body(new ErrorResponse(e.getCode(), e.getMessage()));
  }

  // 컨트롤러 @RequestParam의 @Min/@Max/@Size 등은 클래스에 @Validated가 붙어야
  // 이 예외로 던져진다. 던져지지 않으면 기본 500으로 떨어지므로 여기서 400으로 변환한다.
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException e) {
    String message = e.getConstraintViolations().stream()
        .map(v -> v.getPropertyPath() + ": " + v.getMessage())
        .collect(Collectors.joining(", "));
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("VALIDATION_ERROR", message));
  }

  // NullPointerException은 서버 버그이지 "로그인 안 함" 신호가 아니다.
  // 401로 내리면 프론트가 멀쩡한 세션을 로그아웃시키므로 500으로 응답하고 원인을 로그로 남긴다.
  // 로그인 필요 응답은 Spring Security의 401과 AuthenticationRequiredException이 담당한다.
  @ExceptionHandler(NullPointerException.class)
  public ResponseEntity<ErrorResponse> handleNullPointer(NullPointerException e) {
    log.error("처리되지 않은 NullPointerException", e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new ErrorResponse("INTERNAL_ERROR", "서버 내부 오류가 발생했습니다"));
  }
}
