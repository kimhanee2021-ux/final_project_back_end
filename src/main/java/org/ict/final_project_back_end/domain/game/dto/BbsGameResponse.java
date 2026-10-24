package org.ict.final_project_back_end.domain.game.dto;

import java.util.List;
import org.ict.final_project_back_end.domain.game.repository.GameRepository.BbsGameRow;

/** Only the fields required to choose a game; no descriptions or private library data. */
public record BbsGameResponse(long appId, String name, String genre, String thumbnailUrl) {
  public static BbsGameResponse from(BbsGameRow row) {
    return new BbsGameResponse(row.getAppId(), row.getName(), row.getGenre(), row.getThumbnailUrl());
  }
  public record Search(String query, List<BbsGameResponse> content, boolean hasMore) {}
}
