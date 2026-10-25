package org.ict.final_project_back_end.domain.search.client;

// 한국어 검색어를 모델이 쓰는 영어 키워드로 바꾸는 AI. 실제 구현은 Gemini(GeminiKeywordExtractor)이고, 테스트에서는 가짜로 바꿔 끼운다.
public interface KeywordExtractor {

  // 설정 누락, 호출 실패, 혼잡, 형식에 맞지 않는 답이면 KeywordExtractionException
  SearchKeywords extract(String query);
}
