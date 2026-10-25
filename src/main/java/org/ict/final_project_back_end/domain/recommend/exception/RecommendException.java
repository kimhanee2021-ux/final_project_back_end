package org.ict.final_project_back_end.domain.recommend.exception;

import org.ict.final_project_back_end.global.exception.BusinessException;
import org.springframework.http.HttpStatus;

// AI 게임 추천(FastAPI 추천 서버) 관련 실패. code로 프론트가 안내 문구를 고른다.
public class RecommendException extends BusinessException {

  private RecommendException(HttpStatus status, String code, String message) {
    super(status, code, message);
  }

  // 플레이 시간이 있는 보유 게임이 없거나, 있어도 모델이 아는 게임이 하나도 없는 경우.
  // 오류가 아니라 "게임을 플레이하면 추천해준다" 안내 상태라 프론트가 code로 구분한다.
  public static RecommendException noPlayedGames() {
    return new RecommendException(HttpStatus.CONFLICT, "NO_PLAYED_GAMES", "추천에 쓸 수 있는 플레이한 게임이 없습니다");
  }

  // 추천 서버가 꺼져 있거나 응답이 너무 늦은 경우
  public static RecommendException unavailable() {
    return new RecommendException(HttpStatus.SERVICE_UNAVAILABLE, "RECOMMEND_UNAVAILABLE", "추천 서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요");
  }

  // 추천 서버가 예상하지 못한 오류나 형식에 맞지 않는 답을 준 경우
  public static RecommendException invalidResponse() {
    return new RecommendException(HttpStatus.BAD_GATEWAY, "RECOMMEND_FAILED", "추천을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요");
  }
}
