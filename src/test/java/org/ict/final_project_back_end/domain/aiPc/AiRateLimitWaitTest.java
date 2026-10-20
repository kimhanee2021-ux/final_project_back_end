package org.ict.final_project_back_end.domain.aiPc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicLong;
import org.ict.final_project_back_end.domain.aiCommon.service.AiRateLimit;
import org.junit.jupiter.api.Test;

class AiRateLimitWaitTest {
  @Test
  void 같은_사용자의_두번째_요청은_첫번째가_끝날때까지_기다린다() throws Exception {
    AiRateLimit limit = new AiRateLimit();
    limit.begin(1L);
    AtomicLong waited = new AtomicLong();
    Thread second =
        new Thread(
            () -> {
              long start = System.currentTimeMillis();
              limit.begin(1L);
              waited.set(System.currentTimeMillis() - start);
              limit.end(1L);
            });
    second.start();
    Thread.sleep(300);
    limit.end(1L);
    second.join(5000);
    assertTrue(waited.get() >= 250, "두 번째 요청이 기다리지 않았어요: " + waited.get());
  }

  @Test
  void 분당_횟수_제한은_그대로_적용된다() {
    AiRateLimit limit = new AiRateLimit();
    for (int i = 0; i < 6; i++) {
      limit.begin(2L);
      limit.end(2L);
    }
    var failure =
        org.junit.jupiter.api.Assertions.assertThrows(
            org.ict.final_project_back_end.domain.aiCommon.exception.AiException.class,
            () -> limit.begin(2L));
    assertEquals(429, failure.getStatus().value());
  }
}
