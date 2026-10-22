package org.ict.final_project_back_end.domain.aiCoach.service;

import org.ict.final_project_back_end.global.exception.AiCoachException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// AI 코치 질문 횟수 제한 (Gemini 비용·할당량 보호). 회원당 분당 MAX_PER_MINUTE회, 동시에 한 질문만.
// 서버 메모리에만 두므로 서버를 다시 켜면 초기화된다.
@Component
public class CoachRateLimit {

  static final int MAX_PER_MINUTE = 8;

  private record Window(long minute, int count) {
  }

  private final Map<Long, Window> windows = new HashMap<>();
  private final Set<Long> active = new HashSet<>();

  public synchronized void begin(Long userId) {
    long minute = System.currentTimeMillis() / 60_000;
    windows.entrySet().removeIf(e -> e.getValue().minute() < minute);
    Window previous = windows.get(userId);
    if (active.contains(userId) || (previous != null && previous.count() >= MAX_PER_MINUTE)) {
      throw AiCoachException.tooManyRequests();
    }
    windows.put(userId, new Window(minute, previous == null ? 1 : previous.count() + 1));
    active.add(userId);
  }

  public synchronized void end(Long userId) {
    active.remove(userId);
  }
}
