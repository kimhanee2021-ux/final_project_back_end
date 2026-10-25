package org.ict.final_project_back_end.domain.gamenews.exception;

import org.ict.final_project_back_end.global.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class BbsCommandException extends BusinessException {
  public BbsCommandException(HttpStatus status,String code,String message) { super(status,code,message); }
  public static BbsCommandException invalid() { return new BbsCommandException(HttpStatus.BAD_REQUEST,"BBS_COMMAND_INVALID","게임 이름과 요청을 짧게 말씀해 주세요."); }
  public static BbsCommandException unavailable() { return new BbsCommandException(HttpStatus.BAD_GATEWAY,"BBS_COMMAND_UNAVAILABLE","음성 요청을 이해하지 못했어요. 다시 말씀하거나 직접 검색해 주세요."); }
  public static BbsCommandException notReady() { return new BbsCommandException(HttpStatus.SERVICE_UNAVAILABLE,"BBS_COMMAND_NOT_READY","음성 요청 기능을 준비 중이에요. 직접 검색은 계속 사용할 수 있어요."); }
  public static BbsCommandException limited() { return new BbsCommandException(HttpStatus.TOO_MANY_REQUESTS,"BBS_COMMAND_LIMIT","음성 요청이 많아요. 잠시 후 다시 말씀해 주세요."); }
}
