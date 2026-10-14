package org.ict.final_project_back_end.domain.purchase.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// 스프링/네트워크 없이 이름 규칙만 검증한다 (스팀 확인 단계는 GameNameMatcher.match 안에서 따로 일어난다)
class GameNameMatcherTest {

  private static final Map<String, Long> LIBRARY = new LinkedHashMap<>();

  static {
    LIBRARY.put("Risk of Rain 2", 632360L);
    LIBRARY.put("Hollow Knight", 367520L);
    LIBRARY.put("Portal", 400L);
    LIBRARY.put("The Binding of Isaac", 113200L);
    LIBRARY.put("The Binding of Isaac: Rebirth", 250900L);
  }

  @Test
  void normalizeIgnoresCaseMarksAndPunctuation() {
    assertEquals(GameNameMatcher.normalize("Overwatch®"), GameNameMatcher.normalize("overwatch"));
    assertEquals(GameNameMatcher.normalize("Risk of Rain 2 - Survivors"), GameNameMatcher.normalize("risk of rain 2: survivors"));
    assertEquals("청소계획2", GameNameMatcher.normalize("청소 계획 2"));
    // ™가 "TM" 글자로 바뀌어 남으면 안 된다
    assertEquals(GameNameMatcher.normalize("DARK SOULS III"), GameNameMatcher.normalize("DARK SOULS™ III"));
  }

  @Test
  void colonVariantRewritesOnlyTheFirstDashSeparator() {
    assertEquals("Risk of Rain 2: Survivors of the Void", GameNameMatcher.colonVariant("Risk of Rain 2 - Survivors of the Void"));
    assertNull(GameNameMatcher.colonVariant("Stardew Valley"));
  }

  @Test
  void dlcCandidateNeedsTheOwnedNameFollowedByASeparator() {
    assertEquals(632360L, GameNameMatcher.dlcBaseCandidate("Risk of Rain 2: Seekers of the Storm", LIBRARY));
    assertEquals(632360L, GameNameMatcher.dlcBaseCandidate("Risk of Rain 2 - Survivors of the Void", LIBRARY));
    // 이름 뒤에 구분자 없이 이어지면 다른 게임 (Portal 2 != Portal)
    assertNull(GameNameMatcher.dlcBaseCandidate("Portal 2", LIBRARY));
    // 보유 게임 이름 자체는 후보가 아니다
    assertNull(GameNameMatcher.dlcBaseCandidate("Risk of Rain 2", LIBRARY));
  }

  @Test
  void prefixRuleOnlyMakesACandidateTheSteamCheckDecides() {
    // Silksong은 이름상 후보가 되지만, 실제로는 스팀이 "game"이라고 답해 본편에 묶이지 않는다 (match의 confirmDlc 단계)
    assertEquals(367520L, GameNameMatcher.dlcBaseCandidate("Hollow Knight: Silksong", LIBRARY));
    assertFalse(new SteamAppInfoClient.AppInfo(1030300L, "game", null).isDlcOf(367520L));
    assertTrue(new SteamAppInfoClient.AppInfo(2306620L, "dlc", 632360L).isDlcOf(632360L));
  }

  @Test
  void longestOwnedNameWinsWhenSeveralMatch() {
    assertEquals(250900L, GameNameMatcher.dlcBaseCandidate("The Binding of Isaac: Rebirth - Repentance", LIBRARY));
  }

  @Test
  void convertsToWonWithRounding() {
    assertEquals(7241, PurchaseImportServiceImpl.toKrw(new BigDecimal("452"), new BigDecimal("16.019")));
    assertEquals(36000, PurchaseImportServiceImpl.toKrw(new BigDecimal("36000"), BigDecimal.ONE));
  }

  @Test
  void licenseNamesMatchOwnedGamesAfterRemovingDecorations() {
    Map<String, Long> owned = new LinkedHashMap<>();
    owned.put("Don't Starve Together", 322330L);
    owned.put("Aimlabs", 714010L);
    owned.put("DARK SOULS™ II", 236430L);
    owned.put("Hollow Knight", 367520L);
    owned.put("Hollow Knight: Silksong", 1030300L);

    assertEquals(322330L, GameNameMatcher.matchOwnedLicense("Don't Starve Together Gift", owned));
    assertEquals(714010L, GameNameMatcher.matchOwnedLicense("Aimlabs - Free", owned));
    assertEquals(236430L, GameNameMatcher.matchOwnedLicense("DARK SOULS™ II: Bundle (DX9)", owned));
    // 정확히 같은 이름이 있으면 그 게임이 먼저다 (Silksong을 Hollow Knight로 묶지 않음)
    assertEquals(1030300L, GameNameMatcher.matchOwnedLicense("Hollow Knight: Silksong", owned));
    assertNull(GameNameMatcher.matchOwnedLicense("PUBG: BLINDSPOT Demo", owned));
  }

  @Test
  void ownedNameAtTheEndMatchesTitlesWithAnotherLanguagePrefix() {
    Map<String, Long> owned = new LinkedHashMap<>();
    owned.put("Apocalypse Party", 2059170L);
    owned.put("Portal", 400L);

    assertEquals(2059170L, GameNameMatcher.ownedNameAtEnd("清零计划2：天启派对 Project Zero 2: Apocalypse Party", owned));
    assertEquals(2059170L, GameNameMatcher.matchOwnedLicense("清零计划2：天启派对 Project Zero 2: Apocalypse Party", owned));
    // 8자 미만의 짧은 이름은 끝이 같아도 연결하지 않는다
    assertNull(GameNameMatcher.ownedNameAtEnd("Aperture Tag: The Paint Gun Testing Portal", owned));
    // 바로 앞이 글자면 다른 단어의 일부라서 연결하지 않는다
    assertNull(GameNameMatcher.ownedNameAtEnd("SuperApocalypse Party", owned));
  }
}
