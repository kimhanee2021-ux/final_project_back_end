package org.ict.final_project_back_end.domain.recommendation.exception;

import org.ict.final_project_back_end.global.exception.BusinessException;
import org.springframework.http.HttpStatus;

// 추천 모델 서버(FastAPI) 호출 자체가 실패한 경우(연결 불가, 시간 초과, 서버 오류)
public class RecommendationServerException extends BusinessException {

  public RecommendationServerException() {
    super(HttpStatus.BAD_GATEWAY, "RECOMMEND_SERVER_ERROR", "추천 서버 호출에 실패했습니다");
  }
}
