package org.ict.final_project_back_end.domain.gamenews.client;
import java.util.List;
import org.ict.final_project_back_end.domain.game.service.GameNameResolver.Candidate;
import org.ict.final_project_back_end.domain.gamenews.dto.NewsVoiceIntent.AiContext;

public interface GameCatalogAiClient {
  record Decision(Integer selectedIndex, boolean ambiguous) {}
  List<String> expand(String query, AiContext context);
  Decision choose(String transcript, AiContext context, List<Candidate> candidates);
}
