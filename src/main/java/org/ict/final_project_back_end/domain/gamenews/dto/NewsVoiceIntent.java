package org.ict.final_project_back_end.domain.gamenews.dto;

import java.util.List;
import org.ict.final_project_back_end.domain.game.service.GameNameResolver;

public final class NewsVoiceIntent {
  private NewsVoiceIntent() {}
  public record Context(Long appId, String category, boolean allCategories, String awaiting,
      String candidateSetToken, Long candidateVersion) {}
  public record Turn(String transcript, String action, Long appId, String name, String category, boolean allCategories, String awaiting) {}
  public record AiContext(Long appId, String gameName, String category, boolean allCategories, String awaiting, List<Turn> history) {}
  public record Request(String transcript, Context context, Long version, List<Turn> history) {
    public Request(String transcript, Context context, Long version) { this(transcript, context, version, List.of()); }
  }
  public record Response(String action, Long appId, String name, String category, boolean allCategories,
      String awaiting, List<GameNameResolver.Candidate> candidates, String candidateSetToken,
      Long candidateVersion, boolean hasMore, String support, String briefing, String message) {}
  public record Slots(String gameQuery, String category, boolean allCategories, boolean multipleCategories,
      boolean useCurrentGame, String command, List<String> gameQueries, boolean gameAmbiguous) {
    public Slots(String gameQuery, String category, boolean allCategories, boolean multipleCategories,
        boolean useCurrentGame, String command) {
      this(gameQuery, category, allCategories, multipleCategories, useCurrentGame, command, List.of(), false);
    }
    public Slots(String gameQuery, String category, boolean allCategories, boolean multipleCategories,
        boolean useCurrentGame, String command, List<String> gameQueries) {
      this(gameQuery, category, allCategories, multipleCategories, useCurrentGame, command, gameQueries, false);
    }
    public Slots { gameQueries = gameQueries == null ? List.of() : List.copyOf(gameQueries); }
  }
}
