package org.ict.final_project_back_end.domain.gamenews.exception;

import org.ict.final_project_back_end.global.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class BbsBriefingException extends BusinessException {
  public BbsBriefingException(HttpStatus status, String code, String message) { super(status, code, message); }
  public static BbsBriefingException invalid() { return new BbsBriefingException(HttpStatus.BAD_REQUEST, "BBS_BRIEFING_INVALID", "선택한 소식을 다시 확인해 주세요."); }
  public static BbsBriefingException unavailable() { return new BbsBriefingException(HttpStatus.BAD_GATEWAY, "BBS_BRIEFING_UNAVAILABLE", "요약을 만들지 못했어요. 잠시 후 다시 시도해 주세요."); }
  public static BbsBriefingException notReady() { return new BbsBriefingException(HttpStatus.SERVICE_UNAVAILABLE, "BBS_BRIEFING_NOT_READY", "한국어 요약 기능을 준비 중이에요. 공식 원문은 계속 볼 수 있어요."); }
  public static BbsBriefingException storage() { return new BbsBriefingException(HttpStatus.SERVICE_UNAVAILABLE, "BBS_BRIEFING_STORAGE", "요약 저장소를 사용할 수 없어요. 잠시 후 다시 확인해 주세요."); }
  public static BbsBriefingException changed() { return new BbsBriefingException(HttpStatus.CONFLICT, "BBS_BRIEFING_SOURCE_CHANGED", "소식이 갱신되었어요. 최신 소식을 다시 불러온 뒤 요약해 주세요."); }
  public static BbsBriefingException busy() { return new BbsBriefingException(HttpStatus.CONFLICT, "BBS_BRIEFING_PROCESSING", "이 소식의 요약을 만들고 있어요. 잠시 후 저장 결과를 확인해 주세요."); }
  public static BbsBriefingException limited() { return new BbsBriefingException(HttpStatus.TOO_MANY_REQUESTS, "BBS_BRIEFING_LIMIT", "요약 요청이 많아요. 잠시 후 다시 시도해 주세요."); }
  public static BbsBriefingException insufficient() { return new BbsBriefingException(HttpStatus.UNPROCESSABLE_ENTITY, "BBS_BRIEFING_INSUFFICIENT", "이 소식은 요약할 본문이 부족해요. 공식 원문에서 확인해 주세요."); }
}
