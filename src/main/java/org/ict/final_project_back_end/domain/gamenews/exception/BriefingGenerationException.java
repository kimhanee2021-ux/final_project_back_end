package org.ict.final_project_back_end.domain.gamenews.exception;

/** Safe error code only; never include source text, provider response bodies or credentials. */
public class BriefingGenerationException extends RuntimeException {
  public BriefingGenerationException(String code) { super(code); }
}
