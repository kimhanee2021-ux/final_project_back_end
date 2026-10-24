package org.ict.final_project_back_end.domain.gamenews.service;

import java.time.*;
import java.util.concurrent.atomic.AtomicReference;
import org.ict.final_project_back_end.domain.gamenews.exception.BbsVoiceException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BbsVoiceRequestGuardTest {
  @Test void concurrentMinuteAndDailyLimitsReleaseExactlyOnceAndResetAtTheirBoundaries() {
    var time = new AtomicReference<>(Instant.parse("2026-10-24T00:00:00Z"));
    Clock clock = new Clock() {
      public ZoneId getZone() { return ZoneOffset.UTC; }
      public Clock withZone(ZoneId zone) { return this; }
      public Instant instant() { return time.get(); }
    };
    var guard = new BbsVoiceRequestGuard(3, 1, 1, clock);
    var first = guard.acquire("a");
    assertThrows(BbsVoiceException.class, () -> guard.acquire("b"));
    first.close(); first.close();
    assertThrows(BbsVoiceException.class, () -> guard.acquire("a"));
    var second = guard.acquire("b");
    assertThrows(BbsVoiceException.class, () -> guard.acquire("c"));
    second.close();
    time.set(time.get().plusSeconds(60));
    guard.acquire("a").close();
    assertThrows(BbsVoiceException.class, () -> guard.acquire("d"));
    time.set(time.get().plusSeconds(86400));
    guard.acquire("a").close();
  }
}
