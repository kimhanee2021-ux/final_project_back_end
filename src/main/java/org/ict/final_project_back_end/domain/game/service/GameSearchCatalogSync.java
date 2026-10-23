package org.ict.final_project_back_end.domain.game.service;

import java.util.*;
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
  private final GameRepository games;
  private final GameSearchNameRepository names;
  private final TransactionTemplate transactions;
  public GameSearchCatalogSync(GameRepository games, GameSearchNameRepository names, TransactionTemplate transactions) {
    this.games = games; this.names = names; this.transactions = transactions;
  }
  // 초기 적재가 끝난 ApplicationReadyEvent 이후 실행. 별도 뉴스 수집은 하지 않는다.
  @EventListener(ApplicationReadyEvent.class)
  public void initialize() { synchronize(); }
  @Scheduled(fixedDelayString = "${app.game-search.refresh-ms:300000}", initialDelayString = "${app.game-search.refresh-ms:300000}")
  public synchronized void synchronize() {
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
    }
  }
}
