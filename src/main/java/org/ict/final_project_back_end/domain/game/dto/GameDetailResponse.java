package org.ict.final_project_back_end.domain.game.dto;

import java.math.BigDecimal;
import org.ict.final_project_back_end.domain.game.entity.Game;

// 게임 상세 화면용 응답. 목록 카드 정보에 카테고리·태그·설명 글을 더한다.
// genre/categories/tags는 쉼표로 이어진 글자 그대로 내려가며, tags는 2026년 이후 게임에서 null일 수 있다.
public record GameDetailResponse(Long appId, String name, String genre, String categories, String tags,
    BigDecimal positiveRate, String thumbnailUrl, BigDecimal originalPrice, Integer recommendations,
    String shortDescription, String aboutTheGame) {
  public static GameDetailResponse from(Game game) {
    return new GameDetailResponse(game.getAppId(), game.getName(), game.getGenre(), game.getCategories(),
        game.getTags(), game.getPositiveRate(), game.getThumbnailUrl(), game.getOriginalPrice(),
        game.getRecommendations(), game.getShortDescription(), game.getAboutTheGame());
  }
}
