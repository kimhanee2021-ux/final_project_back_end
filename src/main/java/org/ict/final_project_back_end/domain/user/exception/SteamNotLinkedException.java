package org.ict.final_project_back_end.domain.user.exception;

import org.ict.final_project_back_end.global.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class SteamNotLinkedException extends BusinessException {

  public SteamNotLinkedException() {
    super(HttpStatus.BAD_REQUEST, "STEAM_NOT_LINKED", "스팀 계정이 연동되어 있지 않습니다");
  }
}
