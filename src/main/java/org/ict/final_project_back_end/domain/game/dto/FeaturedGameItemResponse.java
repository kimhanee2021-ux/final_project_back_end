package org.ict.final_project_back_end.domain.game.dto;

import org.ict.final_project_back_end.domain.game.entity.FeaturedCategoryGame;
import org.ict.final_project_back_end.domain.game.entity.FeaturedGame;

public record FeaturedGameItemResponse(Long appId, String name, Boolean discounted, Integer discountPercent,
    Integer originalPrice, Integer finalPrice, String currency, String headerImage) {

  public static FeaturedGameItemResponse from(FeaturedGame game) {
    return new FeaturedGameItemResponse(game.getAppId(), game.getName(), game.getDiscounted(),
        game.getDiscountPercent(), game.getOriginalPrice(), game.getFinalPrice(), game.getCurrency(),
        game.getHeaderImage());
  }

  public static FeaturedGameItemResponse from(FeaturedCategoryGame game) {
    return new FeaturedGameItemResponse(game.getAppId(), game.getName(), game.getDiscounted(),
        game.getDiscountPercent(), game.getOriginalPrice(), game.getFinalPrice(), game.getCurrency(),
        game.getHeaderImage());
  }
}
