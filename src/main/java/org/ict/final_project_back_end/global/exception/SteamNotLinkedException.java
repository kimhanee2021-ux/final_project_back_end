package org.ict.final_project_back_end.global.exception;

import org.springframework.http.HttpStatus;

public class SteamNotLinkedException extends BusinessException {

  public SteamNotLinkedException() {
    super(HttpStatus.BAD_REQUEST, "스팀 계정이 연동되어 있지 않습니다");
  }
}
