package org.ict.final_project_back_end.global.exception;

import org.springframework.http.HttpStatus;

// AI 소비 코치(Gemini) 호출이 실패한 경우. code로 프론트가 안내 문구를 고른다.
public class AiCoachException extends BusinessException {

  private AiCoachException(HttpStatus status, String code, String message) {
    super(status, code, message);
  }

  // 서버에 GEMINI_API_KEY가 없거나 키·모델 설정이 잘못된 경우
  public static AiCoachException notConfigured() {
    return new AiCoachException(HttpStatus.SERVICE_UNAVAILABLE, "AI_NOT_CONFIGURED", "AI 코치 연결 설정이 필요합니다");
  }

  public static AiCoachException tooManyRequests() {
    return new AiCoachException(HttpStatus.TOO_MANY_REQUESTS, "AI_RATE_LIMITED", "질문이 많습니다. 잠시 후 다시 시도해 주세요");
  }

  // Gemini가 다시 시도해도 계속 혼잡한 경우 (HTTP 500/502/503/504)
  public static AiCoachException busy() {
    return new AiCoachException(HttpStatus.SERVICE_UNAVAILABLE, "AI_BUSY", "지금 AI 이용자가 많아요. 잠시 후 다시 시도해 주세요");
  }

  public static AiCoachException timeout() {
    return new AiCoachException(HttpStatus.GATEWAY_TIMEOUT, "AI_TIMEOUT", "AI 응답이 늦어지고 있습니다. 다시 시도해 주세요");
  }

  // Gemini 장애, 형식에 맞지 않는 답, 보유하지 않은 게임 추천 등
  public static AiCoachException invalidResponse() {
    return new AiCoachException(HttpStatus.BAD_GATEWAY, "AI_INVALID_RESPONSE", "AI 답변을 확인하지 못했습니다. 질문을 바꾸거나 다시 시도해 주세요");
  }
}
