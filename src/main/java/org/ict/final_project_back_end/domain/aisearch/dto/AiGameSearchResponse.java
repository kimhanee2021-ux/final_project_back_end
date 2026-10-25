package org.ict.final_project_back_end.domain.aisearch.dto;

import java.math.BigDecimal;
import java.util.List;

// AI 게임 검색 결과.
// intent: AI가 이해한 요청 종류 (SEARCH, FIND_GAME, LATEST_NEWS, NEXT_NEWS, PAUSE, RESUME).
//   NEXT_NEWS/PAUSE/RESUME은 지금 듣는 소식 브리핑 조작이라 검색 결과가 비어 있다.
// matchedGame: 사용자가 말한 특정 게임 (못 찾았거나 말하지 않았으면 null). items에는 넣지 않는다.
// keywords: AI가 문장을 이렇게 이해했다는 키워드 (화면에 "이렇게 찾았어요"로 보여준다)
// ignoredTags: 검색 데이터에 없어서 쓰지 못한 태그, similarTo: "OO 같은 게임"으로 찾은 기준 게임 (없으면 null)
// items는 점수가 높은 순이고, game은 games 테이블에 없는 게임이면 null이다.
public record AiGameSearchResponse(String query, String intent, MatchedGame matchedGame, Keywords keywords,
                                   List<String> ignoredTags, SimilarTo similarTo, List<Item> items) {

  public record Keywords(String gameTitle, List<String> tags, List<String> excludeTags, String description, String similarTo) {
  }

  public record MatchedGame(long appId, GameInfo game) {
  }

  public record SimilarTo(long appId, String name) {
  }

  public record Item(int rank, long appId, double score, GameInfo game) {
  }

  // positiveRate: 스팀 긍정 평가 비율(%), tags: 쉼표로 이은 스팀 태그
  public record GameInfo(String name, String genre, String tags, String thumbnailUrl, BigDecimal positiveRate) {
  }
}
