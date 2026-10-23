package org.ict.final_project_back_end.domain.gamenews.exception;

import org.ict.final_project_back_end.global.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class GameNewsNotFoundException extends BusinessException {
  public GameNewsNotFoundException() {
    super(HttpStatus.NOT_FOUND, "GAME_NEWS_NOT_FOUND", "조회할 수 있는 게임 소식이 없습니다");
  }
}
