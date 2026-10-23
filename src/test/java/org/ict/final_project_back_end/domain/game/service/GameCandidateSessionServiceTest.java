package org.ict.final_project_back_end.domain.game.service;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GameCandidateSessionServiceTest {
  static class MutableClock extends Clock {
    Instant now = Instant.parse("2026-10-24T00:00:00Z");
    public ZoneId getZone() { return ZoneOffset.UTC; }
    public Clock withZone(ZoneId zone) { return this; }
    public Instant instant() { return now; }
  }
  @Test void expiresAtFiveMinutesAndRejectsForeignOwnerVersionOrIndex() {
    var clock = new MutableClock(); var sessions = new GameCandidateSessionService(clock);
    var candidates = List.of(new GameNameResolver.Candidate(570, "Dota 2", "ALIAS"));
    String token = sessions.issue("one", 3, candidates);
    assertEquals(570, sessions.select("one", 3, token, 1));
    assertThrows(GameSearchException.class, () -> sessions.select("two", 3, token, 1));
    assertThrows(GameSearchException.class, () -> sessions.select("one", 2, token, 1));
    assertThrows(GameSearchException.class, () -> sessions.select("one", 3, token, 0));
    String expiring = sessions.issue("one", 4, candidates);
    clock.now = clock.now.plusSeconds(300);
    assertThrows(GameSearchException.class, () -> sessions.select("one", 4, expiring, 1));
  }
  @Test void normalizationPreservesNumbersAndPunctuation() {
    assertEquals("dota 2", GameNameNormalizer.normalize("　ＤＯＴＡ　 ２ "));
    assertNotEquals(GameNameNormalizer.normalize("Game 2"), GameNameNormalizer.normalize("Game 20"));
    assertEquals("game: 2", GameNameNormalizer.normalize("Game: 2"));
  }
}
