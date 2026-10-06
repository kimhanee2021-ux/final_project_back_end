package org.ict.final_project_back_end.domain.game.dto;

import org.ict.final_project_back_end.domain.game.entity.Game;

public record GameResponse(
    Long appId,
    String name
) {

  public static GameResponse from(Game game) {
    return new GameResponse(game.getAppId(), game.getName());
  }
}
