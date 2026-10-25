package org.ict.final_project_back_end.domain.gamenews.exception;

import org.ict.final_project_back_end.global.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class OfficialNewsUnavailableException extends BusinessException {
  public OfficialNewsUnavailableException() {
    super(HttpStatus.SERVICE_UNAVAILABLE, "STEAM_NEWS_UNAVAILABLE",
        "Steam 공식 소식을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.");
  }
}
