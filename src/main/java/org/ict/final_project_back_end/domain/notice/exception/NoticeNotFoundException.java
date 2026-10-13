package org.ict.final_project_back_end.domain.notice.exception;

import org.ict.final_project_back_end.global.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class NoticeNotFoundException extends BusinessException {
  public NoticeNotFoundException() { super(HttpStatus.NOT_FOUND, "NOTICE_NOT_FOUND", "공지사항을 찾을 수 없습니다."); }
}
