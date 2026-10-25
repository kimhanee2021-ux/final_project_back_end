package org.ict.final_project_back_end.domain.recommend.dto;

import java.util.List;

// AI 게임 추천 결과. recommendations는 순위 순서이고, game은 games 테이블에 없는 게임이면 null이다.
public record RecommendationResponse(List<Item> recommendations, Meta meta) {

  // score는 한 번의 추천 안에서 순위를 매기는 값일 뿐 확률·일치율이 아니다
  // source: main = 기본 추천, new = 학습에 없던 신작 칸
  public record Item(int rank, long appId, double score, String source, GameInfo game) {
  }

  public record GameInfo(String name, String genre, String thumbnailUrl) {
  }

  // usedGameCount: 취향 계산에 실제 쓰인 게임 수, unplayedGameCount: 보유했지만 플레이 시간이 0인 게임 수
  public record Meta(int usedGameCount, int unplayedGameCount) {
  }
}
