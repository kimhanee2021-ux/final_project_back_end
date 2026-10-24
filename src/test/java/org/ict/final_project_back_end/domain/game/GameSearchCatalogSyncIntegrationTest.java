package org.ict.final_project_back_end.domain.game;
import jakarta.persistence.EntityManager;
import org.ict.final_project_back_end.domain.game.entity.*;
import org.ict.final_project_back_end.domain.game.repository.*;
import org.ict.final_project_back_end.domain.game.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:game-search-sync;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "app.game-search.sync-enabled=false", "app.seed.games.enabled=false", "app.featured.sync-on-startup=false"})
@Transactional
class GameSearchCatalogSyncIntegrationTest {
  @Autowired EntityManager em;
  @Autowired GameRepository games;
  @Autowired GameSearchNameRepository names;
  @Autowired GameNameResolver resolver;
  @Autowired TransactionTemplate tx;
  @Test void blankCatalogNameIsPreservedAsInactiveAndCanBeActivatedByRename() {
    em.persist(Game.builder().appId(990002L).name("").build()); em.flush();
    var sync = new GameSearchCatalogSync(games, names, tx);
    sync.synchronize(); em.flush();
    assertEquals(1L, em.createQuery("select count(n) from GameSearchName n where n.game.appId=990002 and n.active=false", Long.class).getSingleResult());
    em.createQuery("update Game g set g.name='Now Named 2' where g.appId=990002").executeUpdate(); em.clear();
    sync.synchronize(); em.flush();
    assertTrue(resolver.resolve("Now Named 2").autoSelect());
    assertTrue(names.forGames(java.util.List.of(990002L)).stream().anyMatch(n -> n.kind() == GameSearchName.Kind.CANONICAL));
  }
  @Test void newGamesRenamesAndDeletionNeedNoGameSpecificCode() {
    em.persist(Game.builder().appId(990001L).name("Original 2").build()); em.flush();
    var sync = new GameSearchCatalogSync(games, names, tx);
    sync.synchronize(); em.flush();
    assertTrue(resolver.resolve("Original 2").autoSelect());
    em.createQuery("update Game g set g.name = 'Renamed 2' where g.appId = 990001").executeUpdate(); em.clear();
    sync.synchronize(); em.flush();
    assertTrue(resolver.resolve("Renamed 2").autoSelect());
    assertTrue(resolver.resolve("Original 2").candidates().isEmpty());
    games.deleteById(990001L); games.flush(); em.clear();
    assertTrue(resolver.resolve("Renamed 2").candidates().isEmpty());
  }
}
