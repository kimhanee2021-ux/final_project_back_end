package org.ict.final_project_back_end.domain.gamenews.client;

public interface BbsCommandClient {
  enum Action { SEARCH, LATEST_BRIEFING, NEXT_BRIEFING, PAUSE, RESUME, UNSUPPORTED }
  record Intent(Action action,String gameQuery) {}
  boolean configured();
  Intent interpret(String transcript);
}
