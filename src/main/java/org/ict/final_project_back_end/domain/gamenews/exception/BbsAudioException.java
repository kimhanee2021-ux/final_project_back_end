package org.ict.final_project_back_end.domain.gamenews.exception;

import org.ict.final_project_back_end.global.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class BbsAudioException extends BusinessException {
  public BbsAudioException(HttpStatus status, String code, String message) { super(status,code,message); }
  public static BbsAudioException invalid() { return new BbsAudioException(HttpStatus.BAD_REQUEST,"BBS_AUDIO_INVALID","선택한 소식의 요약을 다시 확인해 주세요."); }
  public static BbsAudioException unavailable() { return new BbsAudioException(HttpStatus.BAD_GATEWAY,"BBS_AUDIO_UNAVAILABLE","음성을 완성하지 못했어요. 잠시 후 다시 눌러 주세요."); }
  public static BbsAudioException notReady() { return new BbsAudioException(HttpStatus.SERVICE_UNAVAILABLE,"BBS_AUDIO_NOT_READY","음성 브리핑을 준비 중이에요. 낭독 대본은 계속 읽을 수 있어요."); }
  public static BbsAudioException changed() { return new BbsAudioException(HttpStatus.CONFLICT,"BBS_AUDIO_BRIEFING_CHANGED","요약이 변경되었거나 아직 준비되지 않았어요. 소식을 다시 선택해 주세요."); }
  public static BbsAudioException busy() { return new BbsAudioException(HttpStatus.CONFLICT,"BBS_AUDIO_PROCESSING","이 소식의 음성을 만들고 있어요. 잠시 후 다시 눌러 주세요."); }
  public static BbsAudioException limited() { return new BbsAudioException(HttpStatus.TOO_MANY_REQUESTS,"BBS_AUDIO_LIMIT","음성 요청이 많아요. 잠시 후 다시 눌러 주세요."); }
}
