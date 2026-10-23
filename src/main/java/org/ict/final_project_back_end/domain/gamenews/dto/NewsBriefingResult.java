package org.ict.final_project_back_end.domain.gamenews.dto;

public record NewsBriefingResult(long newsId, Outcome outcome) {
  public enum Outcome { GENERATED, UP_TO_DATE, BUSY, DEFERRED, STALE, FAILED }
}
