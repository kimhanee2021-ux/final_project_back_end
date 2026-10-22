package org.ict.final_project_back_end.domain.aicommon.service;

import static org.springframework.http.HttpStatus.*;

import java.util.HashMap;
import java.util.Map;
import org.ict.final_project_back_end.domain.aicommon.exception.AiException;
import org.springframework.stereotype.Component;

@Component
public class AiRateLimit {
  private static final long MAX_WAIT_MILLIS = 90_000;

  private record Window(long minute, int count) {}

  private final Map<Long, Window> windows = new HashMap<>();
  private final java.util.Set<Long> active = new java.util.HashSet<>();

  // 같은 사용자의 분석이 아직 끝나지 않았으면 바로 거절하지 않고 끝날 때까지 잠시 기다린다.
  // (브라우저가 요청을 취소해도 서버는 하던 분석을 마치기 때문에, 곧바로 다음 요청이 오면 겹치기 쉽다)
  // 분당 횟수 제한은 그대로 적용한다.
  public synchronized void begin(Long userId) {
    long deadline = System.currentTimeMillis() + MAX_WAIT_MILLIS;
    try {
      while (active.contains(userId)) {
        long left = deadline - System.currentTimeMillis();
        if (left <= 0)
          throw new AiException(TOO_MANY_REQUESTS, "이전 분석이 아직 진행 중이에요. 잠시 후 다시 시도해주세요.");
        wait(left);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AiException(TOO_MANY_REQUESTS, "요청이 중단됐어요. 잠시 후 다시 시도해주세요.");
    }
    begin(userId, 6, windows);
  }

  private void begin(Long userId, int maximum, Map<Long, Window> windows) {
    long minute = System.currentTimeMillis() / 60000;
    windows.entrySet().removeIf(e -> e.getValue().minute() < minute);
    Window previous = windows.get(userId);
    if (active.contains(userId)
        || (previous != null && previous.count() >= maximum)
        || windows.size() >= 10000)
      throw new AiException(TOO_MANY_REQUESTS, "요청이 많습니다. 잠시 후 다시 시도해주세요.");
    windows.put(userId, new Window(minute, previous == null ? 1 : previous.count() + 1));
    active.add(userId);
  }

  public synchronized void end(Long userId) {
    active.remove(userId);
    notifyAll();
  }
}
