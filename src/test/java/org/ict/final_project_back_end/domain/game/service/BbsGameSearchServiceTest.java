package org.ict.final_project_back_end.domain.game.service;

import org.ict.final_project_back_end.domain.game.repository.GameRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// BBS 게임 검색은 게임 이름(과 게임 번호)만으로 찾는다
class BbsGameSearchServiceTest {

  private final GameRepository games = mock(GameRepository.class);
  private final BbsGameSearchService service = new BbsGameSearchService(games);

  private static GameRepository.BbsGameRow row(long appId, String name) {
    return new GameRepository.BbsGameRow() {
      public Long getAppId() { return appId; }
      public String getName() { return name; }
      public String getGenre() { return "Action"; }
      public String getThumbnailUrl() { return null; }
    };
  }

  @Test
  void searchesByNameAndAsksOneMoreRowToKnowIfThereIsMore() {
    when(games.searchForBbsByName("gtf", "%gtf%", null, 13)).thenReturn(List.of(row(493520, "GTFO")));
    when(games.findExactForBbsByName("gtfo", null)).thenReturn(List.of(row(493520, "GTFO")));

    var result = service.search("GTF", 12);
    assertEquals("GTFO", result.content().getFirst().name());
    assertFalse(result.hasMore());
    assertEquals(1, service.exact("GTFO").size());
  }

  @Test
  void numberQueryAlsoMatchesTheAppId() {
    when(games.searchForBbsByName("730", "%730%", 730L, 13)).thenReturn(List.of(row(730, "Counter-Strike 2")));

    assertEquals(730L, service.search("730", 12).content().getFirst().appId());
  }
}
