package org.ict.final_project_back_end.domain.game.service;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GameCatalogNameFormsTest {
  @Test void abbreviationsApplyToArbitraryTitlesAndEditionSuffixes() {
    assertTrue(GameCatalogNameForms.abbreviations("Grand Theft Auto V Legacy").contains("gta"));
    assertTrue(GameCatalogNameForms.abbreviations("Grand Theft Auto V Legacy").contains("gtav"));
    assertTrue(GameCatalogNameForms.abbreviations("Baldur's Gate 3 Playtest").contains("bg3"));
    assertTrue(GameCatalogNameForms.abbreviations("Astral Harbour 4: Deluxe Edition").contains("ah4"));
  }
  @Test void romanAndArabicSequelNumbersMatchWithoutMergingOtherVersions() {
    assertTrue(GameCatalogNameForms.sameNumbers("GTA5", "Grand Theft Auto V"));
    assertFalse(GameCatalogNameForms.sameNumbers("GTA4", "Grand Theft Auto V"));
    assertFalse(GameCatalogNameForms.near("game2", "game3"));
  }
}
