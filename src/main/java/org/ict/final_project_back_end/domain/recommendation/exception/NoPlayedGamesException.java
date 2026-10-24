package org.ict.final_project_back_end.domain.recommendation.exception;

import org.ict.final_project_back_end.global.exception.BusinessException;
import org.springframework.http.HttpStatus;

// 추천에 쓸 플레이한 게임이 없다. 오류가 아니라 안내 상태라 프론트가 code로 분기한다.
public class NoPlayedGamesException extends BusinessException {

  public NoPlayedGamesException() {
    super(HttpStatus.CONFLICT, "NO_PLAYED_GAMES",
        "추천에 사용할 수 있는 플레이한 게임이 없습니다. 스팀 라이브러리를 동기화했는지 확인하세요.");
  }
}
