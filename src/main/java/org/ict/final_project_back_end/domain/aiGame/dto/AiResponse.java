package org.ict.final_project_back_end.domain.aiGame.dto;

import java.time.LocalDateTime;
import java.util.List;
import org.ict.final_project_back_end.domain.game.entity.Game;

public record AiResponse(String answer, List<GameCard> games) {
  public record GameCard(Long appId, String name, String genre, String priceInfo, String thumbnailUrl,
      String storeUrl, LocalDateTime lastSyncedAt, String reason) {
    public static GameCard from(Game game, String reason) {
      return new GameCard(game.getAppId(),game.getName(),game.getGenre(),game.getPriceInfo(),game.getThumbnailUrl(),
          "https://store.steampowered.com/app/"+game.getAppId()+"/",game.getLastSyncedAt(),reason);
    }
  }
}
