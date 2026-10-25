package org.ict.final_project_back_end.domain.search.exception;

import org.ict.final_project_back_end.global.exception.BusinessException;
import org.springframework.http.HttpStatus;

// 자연어 검색 실패. code로 프론트가 안내 문구를 고른다.
public class SearchException extends BusinessException {

  private SearchException(HttpStatus status, String code, String message) {
    super(status, code, message);
  }

  public static SearchException tooManyRequests() {
    return new SearchException(HttpStatus.TOO_MANY_REQUESTS, "SEARCH_RATE_LIMITED", "검색이 너무 많습니다. 잠시 후 다시 시도해 주세요");
  }

  // 검색 모델 서버(FastAPI) 호출 자체가 실패한 경우(연결 불가, 시간 초과, 서버 오류)
  public static SearchException serverError() {
    return new SearchException(HttpStatus.BAD_GATEWAY, "SEARCH_SERVER_ERROR", "검색 서버 호출에 실패했습니다");
  }
}
