package org.ict.final_project_back_end.domain.search.client;

// 검색어를 키워드로 바꾸지 못한 경우. 사용자에게 오류로 보이지 않고, 서비스가 검색어를 그대로 쓰는 대체 경로로 간다.
public class KeywordExtractionException extends RuntimeException {

  public KeywordExtractionException(String message) {
    super(message);
  }
}
