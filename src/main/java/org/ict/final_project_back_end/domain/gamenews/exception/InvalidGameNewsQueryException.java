package org.ict.final_project_back_end.domain.gamenews.exception;

import org.ict.final_project_back_end.global.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class InvalidGameNewsQueryException extends BusinessException {
  public InvalidGameNewsQueryException(String message) {
    super(HttpStatus.BAD_REQUEST, "INVALID_GAME_NEWS_QUERY", message);
  }
}
