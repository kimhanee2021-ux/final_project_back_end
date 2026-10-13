package org.ict.final_project_back_end.domain.game.dto;

import java.math.BigDecimal;
import org.ict.final_project_back_end.domain.game.entity.Game;

public record GameSummaryResponse(Long appId, String name, String genre, String priceInfo,
    Integer discountPercent, Integer reviewCount, BigDecimal positiveRate, String thumbnailUrl) {
  public static GameSummaryResponse from(Game game) {
    return new GameSummaryResponse(game.getAppId(), game.getName(), game.getGenre(), game.getPriceInfo(),
        game.getDiscountPercent(), game.getReviewCount(), game.getPositiveRate(), game.getThumbnailUrl());
  }
}
