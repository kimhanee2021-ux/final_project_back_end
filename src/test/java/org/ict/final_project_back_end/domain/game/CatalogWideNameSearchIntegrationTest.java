package org.ict.final_project_back_end.domain.game;

import jakarta.persistence.EntityManager;
import java.util.*;
import org.ict.final_project_back_end.domain.game.entity.*;
import org.ict.final_project_back_end.domain.game.repository.*;
import org.ict.final_project_back_end.domain.game.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:catalog-wide;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "app.game-search.sync-enabled=false", "app.seed.games.enabled=false", "app.featured.sync-on-startup=false"})
@Transactional
class CatalogWideNameSearchIntegrationTest {
  @Autowired EntityManager em;
  @Autowired GameRepository games;
  @Autowired GameSearchNameRepository aliases;
  @Autowired GameNameResolver resolver;
  @Autowired GameVoiceLexicon vocabulary;
  @Autowired TransactionTemplate tx;
  Game add(long id, String name, String korean) {
    var game = Game.builder().appId(id).name(name).build(); em.persist(game);
    if (korean != null) em.persist(new GameSearchName(game, korean, GameNameNormalizer.normalize(korean), GameSearchName.Kind.ALIAS, "localized-import"));
    return game;
  }
  void sync() { em.flush(); new GameSearchCatalogSync(games, aliases, tx).synchronize(); em.flush(); }
  @Test void arbitraryNewCatalogGameSupportsKoreanEnglishAndGeneratedAcronymWithoutCodeChanges() {
    add(800001, "Astral Harbour 4", "아스트럴 하버 4"); sync();
    assertTrue(resolver.resolve("아스트럴 하버 4").autoSelect());
    assertEquals(800001, resolver.resolve("AstralHarbour4").candidates().getFirst().appId());
    var acronym = resolver.resolve("AH4");
    assertFalse(acronym.autoSelect()); assertEquals(800001, acronym.candidates().getFirst().appId());
    assertTrue(vocabulary.hints("Astral Harbour 4").contains("아스트럴 하버 4"));
    assertFalse(vocabulary.hints("Astral Harbour 4").contains("Dota 2"));
    assertTrue(vocabulary.hints(null).isEmpty());
  }
  @Test void abbreviatedSeriesAndAliasCollisionsAreAlwaysChoices() {
    add(800011, "Silver Garden 2", "실버 가든"); add(800012, "Silver Garden 3", "실버 가든"); sync();
    assertEquals(2, resolver.resolve("SG").candidates().size());
    assertFalse(resolver.resolve("SG").autoSelect());
    assertEquals(2, resolver.resolve("실버 가든").candidates().size());
    assertTrue(resolver.resolve("SG4").candidates().isEmpty());
  }
  @Test void fuzzyMatchingUsesAnyCatalogAliasAndPreservesSequelNumber() {
    add(800021, "Forest Chronicle 7", "포레스트7"); sync();
    var result = resolver.resolve("포레쓰트7");
    assertFalse(result.autoSelect()); assertEquals(800021, result.candidates().getFirst().appId());
    assertEquals("PHONETIC_SUGGESTION", result.candidates().getFirst().match());
    assertTrue(resolver.resolve("포레쓰트8").candidates().isEmpty());
  }
  @Test void renameRemovesOldGeneratedAcronymAndPreservesImportedAlias() {
    add(800031, "Northern Horizon 2", "북쪽 지평선"); sync();
    assertFalse(resolver.resolve("NH2").candidates().isEmpty());
    em.createQuery("update Game g set g.name='Southern Horizon 2' where g.appId=800031").executeUpdate(); em.clear(); sync();
    assertTrue(aliases.exact("nh2", GameSearchName.Kind.ALIAS, org.springframework.data.domain.PageRequest.of(0, 5)).isEmpty());
    assertFalse(resolver.resolve("NH2").autoSelect());
    assertEquals(800031, resolver.resolve("SH2").candidates().getFirst().appId());
    assertEquals(800031, resolver.resolve("북쪽 지평선").candidates().getFirst().appId());
  }
}
