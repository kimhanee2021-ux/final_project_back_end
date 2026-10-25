package org.ict.final_project_back_end.domain.gamenews.exception;

import org.ict.final_project_back_end.global.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class BbsVoiceException extends BusinessException {
  public BbsVoiceException(HttpStatus status, String code, String message) { super(status, code, message); }
  public static BbsVoiceException invalidAudio() {
    return new BbsVoiceException(HttpStatus.BAD_REQUEST, "BBS_AUDIO_INVALID", "4MB 이하의 WebM, MP4 또는 WAV 음성으로 다시 시도해 주세요.");
  }
  public static BbsVoiceException unavailable() {
    return new BbsVoiceException(HttpStatus.BAD_GATEWAY, "BBS_STT_UNAVAILABLE", "음성을 인식하지 못했어요. 잠시 후 다시 시도해 주세요.");
  }
  public static BbsVoiceException notConfigured() {
    return new BbsVoiceException(HttpStatus.SERVICE_UNAVAILABLE, "BBS_STT_NOT_CONFIGURED", "음성 검색이 아직 설정되지 않았어요. 게임 이름을 직접 입력해 주세요.");
  }
  public static BbsVoiceException limited() {
    return new BbsVoiceException(HttpStatus.TOO_MANY_REQUESTS, "BBS_STT_LIMIT", "음성 검색 요청이 많아요. 잠시 후 다시 시도하거나 게임 이름을 직접 입력해 주세요.");
  }
}
