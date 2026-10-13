package org.ict.final_project_back_end.domain.notice.exception;

import org.ict.final_project_back_end.global.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class NoticeForbiddenException extends BusinessException {
  public NoticeForbiddenException() { super(HttpStatus.FORBIDDEN, "NOTICE_FORBIDDEN", "작성자만 공지사항을 수정하거나 삭제할 수 있습니다."); }
}
