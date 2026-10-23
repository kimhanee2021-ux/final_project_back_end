package org.ict.final_project_back_end.domain.gamenews.exception;

import org.ict.final_project_back_end.domain.gamenews.controller.GameNewsController;
import org.ict.final_project_back_end.global.exception.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Limit conversion error formatting to this API; existing domain handlers are unaffected. */
@RestControllerAdvice(assignableTypes = GameNewsController.class)
public class GameNewsQueryExceptionHandler {
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> invalidNumber(MethodArgumentTypeMismatchException error) {
    return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_GAME_NEWS_QUERY", "게임 번호, 소식 번호, 페이지와 크기는 유효한 정수여야 합니다"));
  }
}
