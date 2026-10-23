package org.ict.final_project_back_end.domain.gamenews.service;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.ict.final_project_back_end.domain.game.service.GameSearchException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NewsSpeechServiceTest {
  @Test void replayUsesCacheAndInvalidInputNeverCallsProvider() {
    var calls = new AtomicInteger();
    var service = new NewsSpeechService(text -> { calls.incrementAndGet(); return new byte[48]; });
    for (String bad : new String[]{null, " ", "x".repeat(2401), "bad\u0000"})
      assertThrows(GameSearchException.class, () -> service.speak(bad, "test"));
    assertEquals(0, calls.get());
    assertArrayEquals(service.speak("도타 투", "test"), service.speak("도타 투", "test"));
    assertEquals(1, calls.get());
    for (int i = 0; i < 9; i++) service.speak("소식 " + i, "test");
    assertEquals("SPEECH_LIMIT", assertThrows(GameSearchException.class, () -> service.speak("다른 소식", "test")).getCode());
    service.speak("다른 소식", "another"); assertEquals(11, calls.get());
  }
  @Test void failureIsNotCachedAndConcurrencyIsBounded() throws Exception {
    var calls = new AtomicInteger();
    var service = new NewsSpeechService(text -> {
      if (calls.incrementAndGet() == 1) throw new IllegalStateException("provider failed");
      return new byte[48];
    });
    assertThrows(IllegalStateException.class, () -> service.speak("소식", "test"));
    service.speak("소식", "test"); assertEquals(2, calls.get());
    var entered = new CountDownLatch(2); var release = new CountDownLatch(1);
    var busy = new NewsSpeechService(text -> {
      entered.countDown();
      try { assertTrue(release.await(3, TimeUnit.SECONDS)); } catch (InterruptedException e) { throw new RuntimeException(e); }
      return new byte[48];
    });
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var first = executor.submit(() -> busy.speak("첫 소식", "a"));
      var second = executor.submit(() -> busy.speak("둘째 소식", "b"));
      try {
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        assertEquals("SPEECH_LIMIT", assertThrows(GameSearchException.class, () -> busy.speak("셋째 소식", "c")).getCode());
      } finally { release.countDown(); }
      assertEquals(48, first.get().length); assertEquals(48, second.get().length);
    }
  }
}
