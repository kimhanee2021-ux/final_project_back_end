package org.ict.final_project_back_end.domain.search.dto;

import org.ict.final_project_back_end.domain.game.dto.GameSummaryResponse;

import java.util.List;

// 자연어 검색 결과. analysis는 AI가 검색어를 어떻게 이해했는지(화면에 "이렇게 이해했어요"로 보여 줄 수 있다)
public record NaturalSearchResponse(String query, Analysis analysis, List<Item> results) {

  // analyzed: false면 AI 분석이 실패해 검색어를 그대로 설명 문장으로 써서 검색한 것이다 (정확도가 떨어질 수 있다)
  // ignoredTags: AI가 골랐지만 게임 데이터에 없어서 쓰지 못한 태그
  public record Analysis(boolean analyzed, List<String> tags, List<String> excludeTags, String description,
                         String similarTo, List<String> ignoredTags) {
  }

  // game은 games 카탈로그에 없는 게임이면 null. score는 요청 안에서 순위를 매기는 값이며 확률이나 일치율이 아니다.
  public record Item(int rank, Long appId, double score, GameSummaryResponse game) {
  }
}
