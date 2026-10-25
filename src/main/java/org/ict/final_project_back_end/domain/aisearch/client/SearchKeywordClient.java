package org.ict.final_project_back_end.domain.aisearch.client;

// 사용자의 한국어/영어 문장을 요청 종류 · 게임 이름 · 검색 모델용 영어 키워드로 바꾼다.
// 지금 구현은 OpenAI Responses API(OpenAiSearchKeywordClient)다.
public interface SearchKeywordClient {

  // 키가 없거나 거부되면 AiSearchException.notConfigured, 한도 초과는 limited,
  // 게임을 찾는 문장이 아니면 notUnderstood, 그 밖의 실패는 unavailable
  SearchKeywords extract(String query);
}
