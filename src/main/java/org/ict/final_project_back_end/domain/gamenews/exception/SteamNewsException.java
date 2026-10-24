package org.ict.final_project_back_end.domain.gamenews.exception;

/** Messages contain no response bodies, API credentials or database details. */
public class SteamNewsException extends RuntimeException {
  public SteamNewsException(String message) { super(message); }
  public SteamNewsException(String message, Throwable cause) { super(message, cause); }
}
