package org.ict.final_project_back_end.domain.gamenews.service;

import java.time.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import org.ict.final_project_back_end.domain.game.service.BbsGameSearchService;
import org.ict.final_project_back_end.domain.gamenews.client.SteamNewsClient;
import org.ict.final_project_back_end.domain.gamenews.dto.BbsNewsResponse;
import org.ict.final_project_back_end.domain.gamenews.exception.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** On-demand read-through snapshots. No DB writes, jobs or AI calls in stage 2. */
@Service
public class BbsOfficialNewsService {
  private static final int LIMIT = 40;
  private static final Duration TTL = Duration.ofMinutes(5), STALE_TTL = Duration.ofHours(1);
  private final SteamNewsClient steam;
  private final BbsGameSearchService games;
  private final Clock clock;
  private final Semaphore requests = new Semaphore(4);
  private final Map<Long, Entry> cache = new LinkedHashMap<>(32, .75f, true);

  private static final class Entry {
    BbsNewsResponse snapshot;
    Instant retryAt = Instant.MIN;
  }

  @Autowired
  public BbsOfficialNewsService(SteamNewsClient steam, BbsGameSearchService games) {
    this(steam, games, Clock.systemUTC());
  }

  BbsOfficialNewsService(SteamNewsClient steam, BbsGameSearchService games, Clock clock) {
    this.steam = steam; this.games = games; this.clock = clock;
  }

  public BbsNewsResponse get(long appId) {
    games.find(appId); // Reject IDs outside the existing catalog before contacting Steam.
    Entry entry;
    synchronized (cache) {
      entry = cache.get(appId);
      if (entry == null) {
        if (cache.size() >= 32) cache.remove(cache.keySet().iterator().next());
        entry = new Entry(); cache.put(appId, entry);
      }
    }
    synchronized (entry) {
      Instant now = clock.instant();
      if (entry.snapshot != null && now.isBefore(entry.snapshot.fetchedAt().plus(TTL))) return entry.snapshot;
      if (now.isBefore(entry.retryAt)) return staleOrThrow(entry, now);
      if (!requests.tryAcquire()) return staleOrThrow(entry, now);
      try {
        var rows = steam.fetch(appId, LIMIT);
        var items = rows.stream().map(BbsNewsResponse.Article::from)
            .sorted(Comparator.comparing(BbsNewsResponse.Article::publishedAt).reversed()
                .thenComparing(BbsNewsResponse.Article::id)).toList();
        entry.snapshot = new BbsNewsResponse(appId, clock.instant(), false, LIMIT, items);
        entry.retryAt = Instant.MIN;
        return entry.snapshot;
      } catch (SteamNewsException ex) {
        entry.retryAt = clock.instant().plusSeconds(30);
        return staleOrThrow(entry, clock.instant());
      } finally {
        requests.release();
      }
    }
  }

  private BbsNewsResponse staleOrThrow(Entry entry, Instant now) {
    var previous = entry.snapshot;
    if (previous == null || !now.isBefore(previous.fetchedAt().plus(STALE_TTL)))
      throw new OfficialNewsUnavailableException();
    return new BbsNewsResponse(previous.appId(), previous.fetchedAt(), true, LIMIT, previous.items());
  }
}
