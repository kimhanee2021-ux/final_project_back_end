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
  public Resolution resolve(String query) {
    String key = GameNameNormalizer.query(query);
    var limit = PageRequest.of(0, 6);
    Map<Long, Candidate> matches = new LinkedHashMap<>();
    add(matches, names.exact(key, Kind.CANONICAL, limit), "CANONICAL");
    add(matches, names.exact(key, Kind.ALIAS, limit), "ALIAS");
    // 공식 이름과 별칭의 충돌까지 확인한 후 단일 게임일 때만 확정한다.
    if (matches.size() == 1) return new Resolution(List.copyOf(matches.values()), true, false);
    String escaped = GameNameNormalizer.escapeLike(key);
    add(matches, names.matching(escaped + "%", limit), "PREFIX");
    add(matches, names.matching("%" + escaped + "%", limit), "PARTIAL");
    var all = new ArrayList<>(matches.values());
    return new Resolution(List.copyOf(all.subList(0, Math.min(5, all.size()))), false, all.size() > 5);
  }
  private void add(Map<Long, Candidate> into, List<GameSearchNameRepository.Candidate> rows, String match) {
    rows.forEach(row -> into.putIfAbsent(row.getAppId(), new Candidate(row.getAppId(), row.getName(), match)));
  }
}
