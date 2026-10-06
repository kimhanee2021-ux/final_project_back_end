package org.ict.final_project_back_end.domain.game.dto;

import org.ict.final_project_back_end.domain.game.entity.Game;
import org.ict.final_project_back_end.domain.game.entity.GameDetail;

import java.time.LocalDateTime;
import java.util.List;

public record GameDetailResponse(
    Long appId,
    String name,
    String type,
    Boolean free,
    String shortDescription,
    String headerImageUrl,
    List<String> developers,
    List<String> publishers,
    List<String> genres,
    String releaseDate,
    String currency,
    Integer initialPrice,
    Integer finalPrice,
    Integer discountPercent,
    LocalDateTime fetchedAt
) {

  public static GameDetailResponse from(Game game, GameDetail detail) {
    return new GameDetailResponse(
        game.getAppId(),
        game.getName(),
        detail.getType(),
        detail.getFree(),
        detail.getShortDescription(),
        detail.getHeaderImageUrl(),
        splitLines(detail.getDevelopers()),
        splitLines(detail.getPublishers()),
        splitLines(detail.getGenres()),
        detail.getReleaseDate(),
        detail.getCurrency(),
        detail.getInitialPrice(),
        detail.getFinalPrice(),
        detail.getDiscountPercent(),
        detail.getFetchedAt());
  }

  // GameDetail에 줄바꿈으로 저장한 목록을 다시 리스트로 바꾼다
  public static List<String> splitLines(String joined) {
    return joined == null || joined.isBlank() ? List.of() : List.of(joined.split("\n"));
  }
}
