package org.ict.final_project_back_end.global.exception;

import org.springframework.http.HttpStatus;

public class InvalidSteamAuthException extends BusinessException {

  public InvalidSteamAuthException(String message) {
    super(HttpStatus.UNAUTHORIZED, "INVALID_STEAM_AUTH", message);
  }
}
