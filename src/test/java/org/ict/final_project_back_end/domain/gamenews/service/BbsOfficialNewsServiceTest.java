package org.ict.final_project_back_end.domain.gamenews.service;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.ict.final_project_back_end.domain.game.service.BbsGameSearchService;
import org.ict.final_project_back_end.domain.game.exception.GameNotFoundException;
import org.ict.final_project_back_end.domain.gamenews.client.SteamNewsClient;
import org.ict.final_project_back_end.domain.gamenews.dto.SteamNewsItem;
import org.ict.final_project_back_end.domain.gamenews.exception.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BbsOfficialNewsServiceTest {
  final SteamNewsClient steam = mock(SteamNewsClient.class);
  final BbsGameSearchService games = mock(BbsGameSearchService.class);
  final TestClock clock = new TestClock();
  final BbsOfficialNewsService service = new BbsOfficialNewsService(steam, games, clock);

  @Test void cachesSortsAndKeepsGameSnapshotsSeparate() {
    when(steam.fetch(730, 40)).thenReturn(List.of(item("1", 1), item("18446744073709551615", 3)));
    when(steam.fetch(570, 40)).thenReturn(List.of());
    var response = service.get(730);
    assertEquals("18446744073709551615", response.items().getFirst().id());
    assertEquals(response, service.get(730));
    assertFalse(response.stale());
    assertTrue(service.get(570).items().isEmpty());
    verify(steam, times(1)).fetch(730, 40);
  }

  @Test void unknownGameNeverContactsSteam() {
    when(games.find(999)).thenThrow(new GameNotFoundException());
    assertThrows(GameNotFoundException.class, () -> service.get(999));
    verifyNoInteractions(steam);
  }

  @Test void servesMarkedStaleContentThenExpiresAndBacksOffOnFailures() {
    when(steam.fetch(730, 40)).thenReturn(List.of(item("1", 1))).thenThrow(new SteamNewsException("offline"));
    var original = service.get(730);
    clock.advance(301);
    var stale = service.get(730);
    assertTrue(stale.stale());
    assertEquals(original.fetchedAt(), stale.fetchedAt());
    assertEquals(original.items(), stale.items());
    assertTrue(service.get(730).stale());
    verify(steam, times(2)).fetch(730, 40);
    clock.advance(3600);
    assertThrows(OfficialNewsUnavailableException.class, () -> service.get(730));
  }

  @Test void firstFailureIsNotAnEmptySuccessAndCanRecoverAfterCooldown() {
    when(steam.fetch(730, 40)).thenThrow(new SteamNewsException("offline")).thenReturn(List.of(item("1", 1)));
    assertThrows(OfficialNewsUnavailableException.class, () -> service.get(730));
    assertThrows(OfficialNewsUnavailableException.class, () -> service.get(730));
    verify(steam, times(1)).fetch(730, 40);
    clock.advance(31);
    assertFalse(service.get(730).stale());
  }

  @Test void deduplicatesConcurrentRequestsForTheSameGame() throws Exception {
    CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
    when(steam.fetch(730, 40)).thenAnswer(invocation -> {
      entered.countDown(); assertTrue(release.await(3, TimeUnit.SECONDS)); return List.of(item("1", 1));
    });
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var first = executor.submit(() -> service.get(730));
      assertTrue(entered.await(3, TimeUnit.SECONDS));
      var second = executor.submit(() -> service.get(730));
      release.countDown();
      assertEquals(first.get(3, TimeUnit.SECONDS), second.get(3, TimeUnit.SECONDS));
    } finally { release.countDown(); }
    verify(steam, times(1)).fetch(730, 40);
  }

  @Test void evictsOldGamesAtBoundedCapacity() {
    when(steam.fetch(anyLong(), eq(40))).thenReturn(List.of());
    for (long id = 1; id <= 33; id++) service.get(id);
    service.get(1);
    verify(steam, times(2)).fetch(1, 40);
  }

  SteamNewsItem item(String id, int day) {
    return new SteamNewsItem(730, id, "Update", "[p]Official body[/p]",
        "https://steamcommunity.com/a", LocalDateTime.of(2026, 10, day, 0, 0));
  }
  static class TestClock extends Clock {
    Instant now = Instant.parse("2026-10-24T00:00:00Z");
    void advance(long seconds) { now = now.plusSeconds(seconds); }
    public ZoneId getZone() { return ZoneOffset.UTC; }
    public Clock withZone(ZoneId zone) { return this; }
    public Instant instant() { return now; }
  }
}
