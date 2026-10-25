package org.ict.final_project_back_end.domain.search.service;

import org.ict.final_project_back_end.domain.search.exception.SearchException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

// 자연어 검색 횟수 제한 (Gemini 비용·할당량 보호). 회원당 분당 MAX_PER_MINUTE회.
// 서버 메모리에만 두므로 서버를 다시 켜면 초기화된다.
@Component
public class SearchRateLimit {

  static final int MAX_PER_MINUTE = 10;

  private record Window(long minute, int count) {
  }

  private final Map<Long, Window> windows = new HashMap<>();

  public synchronized void check(Long userId) {
    long minute = System.currentTimeMillis() / 60_000;
    windows.entrySet().removeIf(e -> e.getValue().minute() < minute);
    Window previous = windows.get(userId);
    if (previous != null && previous.count() >= MAX_PER_MINUTE) {
      throw SearchException.tooManyRequests();
    }
    windows.put(userId, new Window(minute, previous == null ? 1 : previous.count() + 1));
  }
}
