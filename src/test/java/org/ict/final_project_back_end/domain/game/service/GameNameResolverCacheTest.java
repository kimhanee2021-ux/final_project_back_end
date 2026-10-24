package org.ict.final_project_back_end.domain.game.service;

import java.util.*;
import org.ict.final_project_back_end.domain.game.repository.GameSearchNameRepository;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class GameNameResolverCacheTest {
  @Test void repeatedExactSearchUsesOneIndexedQueryAndPreservesAliasCollisions() {
    var repository = mock(GameSearchNameRepository.class);
    var first = mock(GameSearchNameRepository.RankedCandidate.class);
    when(first.getAppId()).thenReturn(570L); when(first.getName()).thenReturn("Dota 2"); when(first.getMatchRank()).thenReturn(1);
    when(repository.indexedExact(anyString(),anyString(),any(),any())).thenReturn(List.of(first));
    var resolver = new GameNameResolver(repository);
    assertTrue(resolver.resolve("도타 투").autoSelect()); assertEquals(570L,resolver.resolve("도타 투").candidates().getFirst().appId());
    verify(repository,times(1)).indexedExact(anyString(),anyString(),any(),any()); verifyNoMoreInteractions(repository);
    var second = mock(GameSearchNameRepository.RankedCandidate.class);
    when(second.getAppId()).thenReturn(571L); when(second.getName()).thenReturn("Dota 2 Test"); when(second.getMatchRank()).thenReturn(1);
    when(repository.indexedExact(anyString(),anyString(),any(),any())).thenReturn(List.of(first,second));
    var fresh = new GameNameResolver(repository);
    assertFalse(fresh.resolve("도타 투").autoSelect()); assertEquals(2,fresh.resolve("도타 투").candidates().size());
  }
}
