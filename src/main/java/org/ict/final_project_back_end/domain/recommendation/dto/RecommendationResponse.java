package org.ict.final_project_back_end.domain.recommendation.dto;

import org.ict.final_project_back_end.domain.game.entity.Game;
import org.ict.final_project_back_end.domain.recommendation.entity.RecommendedGame;

import java.util.List;
import java.util.Map;

public record RecommendationResponse(List<RecommendedGameResponse> recommendations, Meta meta) {

  // top: 응답에 담을 추천(순위 순), gamesByAppId: games 카탈로그에서 읽은 게임 정보
  public static RecommendationResponse of(List<RecommendedGame> top, Map<Long, Game> gamesByAppId, int unplayedGameCount) {
    List<RecommendedGameResponse> items = top.stream()
        .map(item -> RecommendedGameResponse.from(item, gamesByAppId.get(item.getAppId())))
        .toList();
    int usedGameCount = top.isEmpty() ? 0 : top.get(0).getUsedGameCount();
    return new RecommendationResponse(items, new Meta(usedGameCount, unplayedGameCount));
  }

  // game은 games 카탈로그에 없는 게임이면 null
  public record RecommendedGameResponse(int rank, Long appId, double score, String source, GameInfo game) {

    public static RecommendedGameResponse from(RecommendedGame recommended, Game game) {
      return new RecommendedGameResponse(recommended.getRank(), recommended.getAppId(), recommended.getScore(),
          recommended.getSource(), game == null ? null : GameInfo.from(game));
    }
  }

  public record GameInfo(String name, String genre, String thumbnailUrl) {

    public static GameInfo from(Game game) {
      return new GameInfo(game.getName(), game.getGenre(), game.getThumbnailUrl());
    }
  }

  // usedGameCount: 취향 계산에 실제 사용된 게임 수, unplayedGameCount: 미플레이라 제외된 보유 게임 수
  public record Meta(int usedGameCount, int unplayedGameCount) {
  }
}
