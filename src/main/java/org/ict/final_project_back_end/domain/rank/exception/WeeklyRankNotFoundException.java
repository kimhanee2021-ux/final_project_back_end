package org.ict.final_project_back_end.domain.rank.exception;

import org.ict.final_project_back_end.global.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class WeeklyRankNotFoundException extends BusinessException {

  public WeeklyRankNotFoundException() {
    super(HttpStatus.NOT_FOUND, "WEEKLY_RANK_NOT_FOUND", "해당 국가와 주의 저장된 순위가 없습니다.");
  }
}
