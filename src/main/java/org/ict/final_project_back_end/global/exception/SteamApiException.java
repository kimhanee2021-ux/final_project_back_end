package org.ict.final_project_back_end.global.exception;

import org.springframework.http.HttpStatus;

// 스팀 Web API 호출 자체가 실패한 경우(네트워크 오류, 잘못된 API 키, 스팀 장애 등)
public class SteamApiException extends BusinessException {

  public SteamApiException() {
    super(HttpStatus.BAD_GATEWAY, "스팀 API 호출에 실패했습니다");
  }
}
