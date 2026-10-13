package org.ict.final_project_back_end.domain.aiCommon.exception;

import org.ict.final_project_back_end.global.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class AiException extends BusinessException {
  public AiException(HttpStatus status, String message){super(status,"AI_"+status.name(),message);}
}
