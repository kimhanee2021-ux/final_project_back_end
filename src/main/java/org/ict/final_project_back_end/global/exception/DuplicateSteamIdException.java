package org.ict.final_project_back_end.global.exception;

import org.springframework.http.HttpStatus;

public class DuplicateSteamIdException extends BusinessException {

  public DuplicateSteamIdException() {
    super(HttpStatus.CONFLICT, "DUPLICATE_STEAM_ID", "이미 다른 계정에 연동된 스팀 계정입니다");
  }
}
