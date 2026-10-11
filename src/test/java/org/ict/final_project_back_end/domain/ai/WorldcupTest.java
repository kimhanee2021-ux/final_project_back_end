package org.ict.final_project_back_end.domain.ai;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WorldcupTest {
  @Test void catalogHasUniqueVerifiedGamesAndSupportsGenreFiltering() {
    var catalog=new WorldcupCatalog();
    assertEquals(237,catalog.games().size());
    assertEquals(237,catalog.games().stream().map(WorldcupGame::appId).distinct().count());
    assertTrue(catalog.games().stream().allMatch(g->!g.description().isBlank() && g.source().startsWith("https://store.steampowered.com/app/")));
    assertFalse(catalog.groups().isEmpty());
  }
  @Test void validationRejectsRepeatedPairsAndSelectedIdOutsidePair() {
    var c=new WorldcupCatalog();var ids=c.games().stream().limit(3).map(WorldcupGame::appId).toList();
    var good=new WorldcupRequest.Choice(ids.get(0),ids.get(1),"both",List.of("전투"),"");
    assertDoesNotThrow(()->WorldcupService.validateHistory(List.of(good),c));
    assertThrows(AiException.class,()->WorldcupService.validateHistory(List.of(good,good),c));
    assertThrows(AiException.class,()->WorldcupService.validateHistory(List.of(new WorldcupRequest.Choice(ids.get(0),ids.get(0),"left",List.of(),"")),c));
    assertThrows(AiException.class,()->WorldcupService.validateHistory(List.of(new WorldcupRequest.Choice(ids.get(0),ids.get(1),"invalid",List.of(),"")),c));
  }
  @Test void candidatePoolNeverRepeatsShownGames() {
    var c=new WorldcupCatalog();var ids=c.games().stream().limit(2).map(WorldcupGame::appId).toList();
    var history=List.of(new WorldcupRequest.Choice(ids.get(0),ids.get(1),"left",List.of(),""));
    assertTrue(c.pool(List.of(),history,"session-1").stream().noneMatch(g->ids.contains(g.appId())));
    assertEquals(48,c.pool(List.of(),history,"session-1").size());
  }
}
