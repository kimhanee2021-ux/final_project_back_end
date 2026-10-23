package org.ict.final_project_back_end.domain.gamenews.dto;

import java.util.List;
import java.util.Map;

public record GameNewsSyncResult(boolean alreadyRunning, boolean interrupted, List<GameResult> games) {
  public enum CollectionStatus { COLLECTED, NOT_DUE, FAILED }
  public record GameResult(long appId, CollectionStatus collectionStatus, GameNewsCollectionResult collection,
      Map<NewsBriefingResult.Outcome, Integer> briefings, int briefingErrors) { }
}
