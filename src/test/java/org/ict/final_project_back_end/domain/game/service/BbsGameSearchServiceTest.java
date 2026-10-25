package org.ict.final_project_back_end.domain.game.service;

import org.ict.final_project_back_end.domain.game.repository.GameRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// 별명 테이블(game_search_names)이 없는 DB에서는 그 테이블을 조회하지 않고 게임 이름만으로 찾아야 한다 (없으면 500)
class BbsGameSearchServiceTest {

  private final GameRepository games = mock(GameRepository.class);
  private final GameSearchNameTable aliases = mock(GameSearchNameTable.class);
  private final BbsGameSearchService service = new BbsGameSearchService(games, aliases);

  private static GameRepository.BbsGameRow row(long appId, String name) {
    return new GameRepository.BbsGameRow() {
      public Long getAppId() { return appId; }
      public String getName() { return name; }
      public String getGenre() { return "Action"; }
      public String getThumbnailUrl() { return null; }
    };
  }

  @Test
  void withoutAliasTableSearchesByNameOnly() {
    when(aliases.available()).thenReturn(false);
    when(games.searchForBbsByName("gtf", "%gtf%", null, 13)).thenReturn(List.of(row(493520, "GTFO")));
    when(games.findExactForBbsByName("gtfo", null)).thenReturn(List.of(row(493520, "GTFO")));

    assertEquals("GTFO", service.search("GTF", 12).content().getFirst().name());
    assertEquals(1, service.exact("GTFO").size());
    verify(games, never()).searchForBbs(anyString(), anyString(), any(), any(Pageable.class));
    verify(games, never()).findExactForBbs(anyString(), any(), any(Pageable.class));
  }

  @Test
  void withAliasTableKeepsAliasSearch() {
    when(aliases.available()).thenReturn(true);
    when(games.searchForBbs(eq("카스"), eq("%카스%"), isNull(), any(Pageable.class))).thenReturn(List.of(row(730, "Counter-Strike 2")));

    assertEquals("Counter-Strike 2", service.search("카스", 12).content().getFirst().name());
    verify(games, never()).searchForBbsByName(anyString(), anyString(), any(), anyInt());
  }
}
