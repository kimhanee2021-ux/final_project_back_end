package org.ict.final_project_back_end.domain.rank.exception;

public class SteamRankFetchException extends RuntimeException {
  public SteamRankFetchException(String message) { super(message); }
  public SteamRankFetchException(String message, Throwable cause) { super(message, cause); }
}
