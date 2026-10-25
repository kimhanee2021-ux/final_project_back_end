package org.ict.final_project_back_end.domain.search.client;

import java.util.List;

// LLM이 한국어 검색어를 분석해 만든 영어 키워드. 비어 있는 항목은 모델이 쓰지 않는다.
// tags/excludeTags는 Steam 태그 이름, description은 원하는 게임을 설명하는 영어 한 문장, similarTo는 "OO 같은 게임"의 영어 이름이다.
public record SearchKeywords(List<String> tags, List<String> excludeTags, String description, String similarTo) {

  public SearchKeywords {
    tags = clean(tags);
    excludeTags = clean(excludeTags);
    description = description == null ? "" : description.trim();
    similarTo = similarTo == null ? "" : similarTo.trim();
  }

  // LLM을 쓸 수 없을 때: 검색어를 그대로 설명 문장으로 쓴다 (모델의 다국어 임베딩이 한국어도 어느 정도 이해한다)
  public static SearchKeywords fallback(String query) {
    return new SearchKeywords(List.of(), List.of(), query, "");
  }

  // 찾을 대상(태그, 설명, 비슷한 게임)이 하나라도 있는가. 제외 태그만으로는 검색할 수 없다
  public boolean hasPositive() {
    return !tags.isEmpty() || !description.isEmpty() || !similarTo.isEmpty();
  }

  public SearchKeywords withDescription(String newDescription) {
    return new SearchKeywords(tags, excludeTags, newDescription, similarTo);
  }

  private static List<String> clean(List<String> names) {
    if (names == null) {
      return List.of();
    }
    return names.stream().filter(n -> n != null && !n.isBlank()).map(String::trim).distinct().toList();
  }
}
