package org.ict.final_project_back_end.domain.game.service;

import org.ict.final_project_back_end.global.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class GameSearchException extends BusinessException {
  public GameSearchException(HttpStatus status, String code, String message) { super(status, code, message); }
  public static GameSearchException invalid(String message) {
    return new GameSearchException(HttpStatus.BAD_REQUEST, "INVALID_GAME_SEARCH", message);
  }
}
