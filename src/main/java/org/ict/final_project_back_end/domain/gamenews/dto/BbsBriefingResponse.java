package org.ict.final_project_back_end.domain.gamenews.dto;

import java.time.Instant;
import java.util.List;

public record BbsBriefingResponse(String status, Briefing briefing) {
  public static BbsBriefingResponse state(String status) { return new BbsBriefingResponse(status, null); }
  public record Briefing(String titleKo, List<String> points, String narrationScript,
      String sourceHash, String model, String promptVersion, Instant generatedAt,
      int estimatedSeconds, String sourceUrl, Instant publishedAt) {}
}
