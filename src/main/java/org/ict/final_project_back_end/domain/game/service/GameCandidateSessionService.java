package org.ict.final_project_back_end.domain.game.service;

import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class GameCandidateSessionService {
  private record SetEntry(String owner, long version, List<Long> ids, Instant expires) {}
  private final Map<String, SetEntry> sets = new HashMap<>();
  private final Clock clock;
  public GameCandidateSessionService() { this(Clock.systemUTC()); }
  GameCandidateSessionService(Clock clock) { this.clock = clock; }
  public synchronized String issue(String owner, long version, List<GameNameResolver.Candidate> candidates) {
    sets.entrySet().removeIf(e -> !e.getValue().expires().isAfter(clock.instant()) || e.getValue().owner().equals(owner));
    if (sets.size() >= 10000) throw new GameSearchException(HttpStatus.TOO_MANY_REQUESTS, "SEARCH_BUSY", "잠시 후 다시 검색해 주세요.");
    String token = UUID.randomUUID().toString();
    sets.put(token, new SetEntry(owner, version, candidates.stream().map(GameNameResolver.Candidate::appId).toList(), clock.instant().plusSeconds(300)));
    return token;
  }
  public synchronized long select(String owner, long version, String token, int index) {
    var entry = sets.get(token);
    if (entry == null || !entry.owner().equals(owner) || entry.version() != version || !entry.expires().isAfter(clock.instant()))
      throw new GameSearchException(HttpStatus.CONFLICT, "CANDIDATES_EXPIRED", "후보가 만료되었거나 현재 검색과 다릅니다. 다시 검색해 주세요.");
    if (index < 1 || index > entry.ids().size()) throw GameSearchException.invalid("현재 후보 목록의 번호를 선택해 주세요.");
    sets.remove(token);
    return entry.ids().get(index - 1);
  }
  public synchronized void clear(String owner) { sets.entrySet().removeIf(e -> e.getValue().owner().equals(owner)); }
}
