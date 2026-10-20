package org.ict.final_project_back_end.domain.game.dto;

import java.math.BigDecimal;
import org.ict.final_project_back_end.domain.game.entity.Game;

// 게임 목록/검색 카드에 필요한 최소 정보. 긴 설명 글은 상세 응답(GameDetailResponse)에서만 내려준다.
// genre는 "Action,RPG"처럼 쉼표로 이어진 글자 그대로 내려간다.
public record GameSummaryResponse(Long appId, String name, String genre, BigDecimal positiveRate,
    String thumbnailUrl, BigDecimal originalPrice, Integer recommendations) {
  public static GameSummaryResponse from(Game game) {
    return new GameSummaryResponse(game.getAppId(), game.getName(), game.getGenre(),
        game.getPositiveRate(), game.getThumbnailUrl(), game.getOriginalPrice(),
        game.getRecommendations());
  }
}
