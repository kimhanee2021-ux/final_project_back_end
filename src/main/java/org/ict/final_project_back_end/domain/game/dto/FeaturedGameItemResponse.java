package org.ict.final_project_back_end.domain.game.dto;

import java.util.List;

import org.ict.final_project_back_end.domain.game.entity.FeaturedCategoryGame;
import org.ict.final_project_back_end.domain.game.entity.FeaturedGame;

// tags: 게임 카드에 붙일 대표 태그(장르) 최대 3개. games 카탈로그에 없는 게임은 빈 목록이다.
public record FeaturedGameItemResponse(Long appId, String name, Boolean discounted, Integer discountPercent,
    Integer originalPrice, Integer finalPrice, String currency, String headerImage, List<String> tags) {

  public static FeaturedGameItemResponse from(FeaturedGame game, List<String> tags) {
    return new FeaturedGameItemResponse(game.getAppId(), game.getName(), game.getDiscounted(),
        game.getDiscountPercent(), game.getOriginalPrice(), game.getFinalPrice(), game.getCurrency(),
        game.getHeaderImage(), tags);
  }

  public static FeaturedGameItemResponse from(FeaturedCategoryGame game, List<String> tags) {
    return new FeaturedGameItemResponse(game.getAppId(), game.getName(), game.getDiscounted(),
        game.getDiscountPercent(), game.getOriginalPrice(), game.getFinalPrice(), game.getCurrency(),
        game.getHeaderImage(), tags);
  }
}
