package org.ict.final_project_back_end.domain.notice.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.ict.final_project_back_end.domain.notice.dto.NoticeValidationError;

@RestControllerAdvice(assignableTypes = NoticeController.class)
public class NoticeValidationHandler {
  @ExceptionHandler({HandlerMethodValidationException.class, MethodArgumentTypeMismatchException.class})
  public ResponseEntity<NoticeValidationError> invalidParameter(Exception exception) {
    return ResponseEntity.badRequest().body(new NoticeValidationError(
        "VALIDATION_ERROR", "페이지와 입력값을 확인해주세요.", Map.of()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<NoticeValidationError> invalidInput(MethodArgumentNotValidException exception) {
    Map<String, String> errors = new LinkedHashMap<>();
    exception.getBindingResult().getFieldErrors()
        .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
    return ResponseEntity.badRequest().body(new NoticeValidationError("VALIDATION_ERROR", "입력값을 확인해주세요.", errors));
  }
}
