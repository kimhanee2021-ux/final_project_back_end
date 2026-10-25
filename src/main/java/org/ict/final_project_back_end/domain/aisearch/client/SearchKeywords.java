package org.ict.final_project_back_end.domain.aisearch.client;

import java.util.List;

// 사용자 문장에서 뽑은 요청 종류와 영어 검색 키워드.
// intent: 무엇을 원하는지 (아래 Intent), gameTitle: 사용자가 특정 게임을 말했으면 그 게임의 영어 공식 제목
// tags/excludeTags/description/similarTo: 검색 모델(FastAPI)이 받는 모양 그대로. 없는 항목은 빈 값이다.
public record SearchKeywords(Intent intent, String gameTitle, List<String> tags, List<String> excludeTags,
                             String description, String similarTo) {

  // SEARCH: 조건에 맞는 게임 찾기, FIND_GAME: 특정 게임 찾기, LATEST_NEWS: 특정 게임의 최신 공식 소식 듣기,
  // NEXT_NEWS/PAUSE/RESUME: 지금 듣는 소식 브리핑 조작 (게임·키워드 없음)
  public enum Intent {
    SEARCH, FIND_GAME, LATEST_NEWS, NEXT_NEWS, PAUSE, RESUME;

    public boolean isControl() {
      return this == NEXT_NEWS || this == PAUSE || this == RESUME;
    }
  }

  // 검색 모델에 넘길 키워드가 하나도 없는지 (게임 이름은 따로 본다)
  public boolean hasNoKeywords() {
    return tags.isEmpty() && description.isBlank() && similarTo.isBlank();
  }
}
