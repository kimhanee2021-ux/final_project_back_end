package org.ict.final_project_back_end.domain.game.service;

import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.ict.final_project_back_end.domain.game.entity.*;
import org.ict.final_project_back_end.domain.game.repository.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(name = "app.game-search.sync-enabled", havingValue = "true", matchIfMissing = true)
public class GameSearchCatalogSync {
  private record AliasSeed(String canonical, List<String> aliases) {}
  private final Map<String, List<String>> aliasSeeds = loadAliasSeeds();
  private Map<String, List<String>> loadAliasSeeds() {
    try (var in = new ClassPathResource("game-voice-names.json").getInputStream()) {
      Map<String, List<String>> result = new HashMap<>();
      for (var seed : new ObjectMapper().readValue(in, AliasSeed[].class))
        result.put(GameNameNormalizer.normalize(seed.canonical()), seed.aliases());
      return result;
    } catch (java.io.IOException error) { throw new IllegalStateException("Could not load supplemental catalog aliases", error); }
  }
  private final GameRepository games;
  private final GameSearchNameRepository names;
  private final TransactionTemplate transactions;
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;
  public GameSearchCatalogSync(GameRepository games, GameSearchNameRepository names, TransactionTemplate transactions) {
    this(games, names, transactions, null);
  }
  @org.springframework.beans.factory.annotation.Autowired
  public GameSearchCatalogSync(GameRepository games, GameSearchNameRepository names, TransactionTemplate transactions,
      org.springframework.jdbc.core.JdbcTemplate jdbc) {
    this.games = games; this.names = names; this.transactions = transactions; this.jdbc = jdbc;
  }
  private record MissingKey(long id, String name) {}
  private void backfillSearchKeys() {
    if (jdbc == null) return;
    while (true) {
      var rows = jdbc.query("select id, normalized_name from game_search_names where compact_name is null order by id limit 500",
          (rs, row) -> new MissingKey(rs.getLong(1), rs.getString(2)));
      if (rows.isEmpty()) return;
      StringBuilder sql = new StringBuilder("update game_search_names set compact_name = case id ");
      List<Object> args = new ArrayList<>();
      for (var row : rows) { sql.append("when ? then ? "); args.add(row.id()); args.add(GameVoiceLexicon.key(row.name())); }
      sql.append("end where compact_name is null and id in (").append(String.join(",", Collections.nCopies(rows.size(), "?"))).append(")");
      rows.forEach(row -> args.add(row.id()));
      jdbc.update(sql.toString(), args.toArray());
      GameSearchName.searchKeysChanged();
    }
  }
  // 초기 적재가 끝난 ApplicationReadyEvent 이후 실행. 별도 뉴스 수집은 하지 않는다.
  @EventListener(ApplicationReadyEvent.class)
  public void initialize() { synchronize(); }
  @Scheduled(fixedDelayString = "${app.game-search.refresh-ms:300000}", initialDelayString = "${app.game-search.refresh-ms:300000}")
  public synchronized void synchronize() {
    backfillSearchKeys();
    long after = 0;
    while (true) {
      List<Long> ids = games.findAppIdsAfter(after, PageRequest.of(0, 500));
      if (ids.isEmpty()) return;
      transactions.executeWithoutResult(status -> syncBatch(ids));
      after = ids.getLast();
    }
  }
  private void syncBatch(List<Long> ids) {
    var existing = names.forGames(ids);
    existing.forEach(GameSearchName::refreshSearchKey);
    Map<Long, List<GameSearchName>> byGame = new HashMap<>();
    existing.forEach(n -> byGame.computeIfAbsent(n.appId(), id -> new ArrayList<>()).add(n));
    for (Game game : games.findAllById(ids)) {
      String normalized = GameNameNormalizer.normalize(game.getName());
      if (normalized.length() > 255) continue;
      var rows = byGame.getOrDefault(game.getAppId(), List.of());
      rows.stream().filter(n -> n.kind() == GameSearchName.Kind.CANONICAL && !n.normalizedName().equals(normalized))
          .forEach(names::delete);
      var matching = rows.stream().filter(n -> n.normalizedName().equals(normalized)).findFirst();
      if (matching.isPresent()) matching.get().canonical(game.getName());
      else names.save(new GameSearchName(game, game.getName(), normalized, GameSearchName.Kind.CANONICAL, "games"));
      Map<String, String> desired = new LinkedHashMap<>();
      for (String alias : aliasSeeds.getOrDefault(normalized, List.of()))
        desired.put(GameNameNormalizer.normalize(alias), "catalog-localized");
      List<String> allNames = new ArrayList<>(); allNames.add(game.getName());
      rows.stream().filter(n -> n.kind() == GameSearchName.Kind.ALIAS && !n.source().startsWith("catalog-"))
          .forEach(n -> allNames.add(n.displayName()));
      for (String name : allNames) for (String abbreviation : GameCatalogNameForms.abbreviations(name))
        desired.putIfAbsent(abbreviation, "catalog-acronym");
      desired.remove(normalized);
      rows.stream().filter(n -> n.source().startsWith("catalog-") && !desired.containsKey(n.normalizedName())).forEach(names::delete);
      Set<String> existingKeys = new HashSet<>(); rows.forEach(n -> existingKeys.add(n.normalizedName()));
      for (var alias : desired.entrySet()) if (!existingKeys.contains(alias.getKey()))
        names.save(new GameSearchName(game, alias.getKey(), alias.getKey(), GameSearchName.Kind.ALIAS, alias.getValue()));
    }
  }
}
