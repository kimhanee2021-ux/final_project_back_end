package org.ict.final_project_back_end.domain.game.service;

import org.ict.final_project_back_end.domain.game.dto.GameDetailResponse;
import org.ict.final_project_back_end.domain.game.dto.GamePageResponse;

public interface GameService {
  GamePageResponse listGames(int page, int size, String genre);
  GamePageResponse searchGames(int page, int size, String name);
  GameDetailResponse getGame(Long appId);
}
