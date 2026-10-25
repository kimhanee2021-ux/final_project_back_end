package org.ict.final_project_back_end.domain.aisearch.exception;

import org.ict.final_project_back_end.global.exception.BusinessException;
import org.springframework.http.HttpStatus;

// AI 게임 검색(GPT 키워드 추출 + FastAPI 검색 모델) 실패. code로 프론트가 안내 문구를 고른다.
public class AiSearchException extends BusinessException {

  private AiSearchException(HttpStatus status, String code, String message) {
    super(status, code, message);
  }

  // 게임을 찾는 요청으로 읽히지 않거나, 뽑은 키워드를 검색 모델이 하나도 쓰지 못한 경우
  public static AiSearchException notUnderstood() {
    return new AiSearchException(HttpStatus.UNPROCESSABLE_CONTENT, "AI_SEARCH_NOT_UNDERSTOOD",
        "어떤 게임을 찾는지 조금 더 구체적으로 적어 주세요. 예: 친구랑 같이 하는 무서운 협동 게임");
  }

  // 서버에 OPENAI_API_KEY가 없거나 키가 거부된 경우
  public static AiSearchException notConfigured() {
    return new AiSearchException(HttpStatus.SERVICE_UNAVAILABLE, "AI_SEARCH_NOT_CONFIGURED", "AI 검색 연결 설정이 필요합니다");
  }

  public static AiSearchException limited() {
    return new AiSearchException(HttpStatus.TOO_MANY_REQUESTS, "AI_SEARCH_LIMITED", "검색 요청이 많아요. 잠시 후 다시 시도해 주세요");
  }

  // GPT 또는 검색 모델 서버가 꺼져 있거나, 늦거나, 형식에 맞지 않는 답을 준 경우
  public static AiSearchException unavailable() {
    return new AiSearchException(HttpStatus.SERVICE_UNAVAILABLE, "AI_SEARCH_UNAVAILABLE", "지금은 AI 검색을 할 수 없어요. 잠시 후 다시 시도해 주세요");
  }
}
