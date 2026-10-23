package org.ict.final_project_back_end.domain.gamenews.service;
import java.time.*;
import org.ict.final_project_back_end.domain.game.service.GameSearchException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NewsVoiceAiLimitsTest {
  final Clock clock = Clock.fixed(Instant.parse("2026-10-24T00:00:00Z"), ZoneOffset.UTC);
  @Test void dailySessionRemoteAndConcurrencyLimitsReleaseSafely() {
    var budget = new NewsVoiceAiLimits(2, 20, 1, clock);
    var permit = budget.acquire("one", "ip");
    assertThrows(GameSearchException.class, () -> budget.acquire("two", "ip"));
    permit.close(); permit.close();
    budget.acquire("one", "ip").close();
    assertThrows(GameSearchException.class, () -> budget.acquire("new", "new-ip"));
    var perMinute = new NewsVoiceAiLimits(100, 1, 2, clock);
    perMinute.acquire("one", "ip").close();
    assertThrows(GameSearchException.class, () -> perMinute.acquire("one", "other-ip"));
    assertThrows(GameSearchException.class, () -> perMinute.acquire("other", "ip"));
  }
}
