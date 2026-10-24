package org.ict.final_project_back_end.domain.game.service;

import java.util.*;
import org.ict.final_project_back_end.domain.game.entity.GameSearchName.Kind;
import org.ict.final_project_back_end.domain.game.repository.GameSearchNameRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GameNameResolver {
  public record Candidate(long appId, String name, String match) {}
  public record Resolution(List<Candidate> candidates, boolean autoSelect, boolean hasMore) {}
  private final GameSearchNameRepository names;
  public GameNameResolver(GameSearchNameRepository names) { this.names = names; }
  private record Cached(Resolution result, long expires, long revision) {}
  private final Map<String, Cached> cache = new LinkedHashMap<>(64, .75f, true);
  public Resolution resolve(String query) {
    String key = GameNameNormalizer.query(query);
    // Never cache results read inside a caller's uncommitted write transaction.
    boolean cacheable = !org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()
        || org.springframework.transaction.support.TransactionSynchronizationManager.isCurrentTransactionReadOnly();
    long now = System.currentTimeMillis(), revision = org.ict.final_project_back_end.domain.game.entity.GameSearchName.revision();
    if (cacheable) synchronized (cache) {
      cache.entrySet().removeIf(e -> e.getValue().expires() <= now || e.getValue().revision() != revision);
      var hit = cache.get(key); if (hit != null) return hit.result();
    }
    var result = lookup(key);
    if (cacheable) synchronized (cache) {
      cache.put(key, new Cached(result, now + (result.candidates().isEmpty() ? 5_000 : 30_000), revision));
      while (cache.size() > 512) cache.remove(cache.keySet().iterator().next());
    }
    return result;
  }
  private Resolution lookup(String key) {
    var limit = PageRequest.of(0, 6);
    Map<Long, Candidate> matches = new LinkedHashMap<>();
    String compact = GameVoiceLexicon.key(key);
    if (!compact.isEmpty()) {
      for (var row : names.indexedExact(compact, key, Kind.CANONICAL, limit))
        matches.put(row.getAppId(), new Candidate(row.getAppId(), row.getName(),
            row.getMatchRank() == 0 ? "CANONICAL" : row.getMatchRank() == 1 ? "ALIAS" : "NORMALIZED"));
    } else {
      add(matches, names.exact(key, Kind.CANONICAL, limit), "CANONICAL");
      add(matches, names.exact(key, Kind.ALIAS, limit), "ALIAS");
    }
    // 공식 이름과 별칭의 충돌까지 확인한 후 단일 게임일 때만 확정한다.
    if (!matches.isEmpty()) {
      var exact = new ArrayList<>(matches.values());
      return new Resolution(List.copyOf(exact.subList(0, Math.min(5, exact.size()))),
          exact.size() == 1 && !isAbbreviation(key, exact.getFirst()), exact.size() > 5);
    }
    String escaped = GameNameNormalizer.escapeLike(key);
    add(matches, names.matching(escaped + "%", limit), "PREFIX");
    add(matches, names.matching("%" + escaped + "%", limit), "PARTIAL");
    if (matches.isEmpty() && compact.length() >= 3) {
      String prefix = GameNameNormalizer.escapeLike(key.substring(0, Math.min(2, key.length()))) + "%";
      String suffix = "%" + GameNameNormalizer.escapeLike(key.substring(Math.max(0, key.length() - 2)));
      for (var row : names.nearby(Math.max(1, key.length() - 1), key.length() + 1, prefix, suffix, PageRequest.of(0, 100))) {
        if (GameCatalogNameForms.near(compact, GameVoiceLexicon.key(row.getSearchName())))
          matches.putIfAbsent(row.getAppId(), new Candidate(row.getAppId(), row.getName(), "PHONETIC_SUGGESTION"));
      }
    }
    var all = new ArrayList<>(matches.values());
    return new Resolution(List.copyOf(all.subList(0, Math.min(5, all.size()))), false, all.size() > 5);
  }
  private void add(Map<Long, Candidate> into, List<GameSearchNameRepository.Candidate> rows, String match) {
    rows.forEach(row -> into.putIfAbsent(row.getAppId(), new Candidate(row.getAppId(), row.getName(), match)));
  }
  private boolean isAbbreviation(String query, Candidate candidate) {
    return !candidate.match().equals("CANONICAL") && GameCatalogNameForms.abbreviations(candidate.name()).contains(GameVoiceLexicon.key(query));
  }
}
