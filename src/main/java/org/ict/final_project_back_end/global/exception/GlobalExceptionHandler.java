package org.ict.final_project_back_end.global.exception;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

// 서비스에서 던진 BusinessException을 한 곳에서 HTTP 응답으로 변환한다.
// 덕분에 서비스는 HTTP를 몰라도 되고(순수 비즈니스 로직), 컨트롤러도 에러 응답 포맷을 신경 쓰지 않는다.
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
    return ResponseEntity.status(e.getStatus()).body(new ErrorResponse(e.getMessage()));

  }

  // 컨트롤러 @RequestParam의 @Min/@Max/@Size 등은 클래스에 @Validated가 붙어야
  // 이 예외로 던져진다. 던져지지 않으면 기본 500으로 떨어지므로 여기서 400으로 변환한다.
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException e) {
    String message = e.getConstraintViolations().stream()
        .map(v -> v.getPropertyPath() + ": " + v.getMessage())
        .collect(Collectors.joining(", "));
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(message));
  }

  @ExceptionHandler(NullPointerException.class)
  public ResponseEntity<Map<String, String>> handleNullPointer(NullPointerException e) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "로그인이 필요합니다"));
  }
}

