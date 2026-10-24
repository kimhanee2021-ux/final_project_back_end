package org.ict.final_project_back_end.domain.recommendation.service;

import org.ict.final_project_back_end.domain.recommendation.entity.RecommendedGame;
import org.ict.final_project_back_end.domain.recommendation.repository.RecommendedGameRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// 저장된 추천을 다시 써도 되는지(개수 / TTL) 판단만 검증한다
class RecommendationStoreTest {

  private static final long USER_ID = 5L;

  private final RecommendedGameRepository repository = mock(RecommendedGameRepository.class);
  private final RecommendationStore store = new RecommendationStore(repository, 24);

  @Test
  void reusesFreshSavedRecommendations() {
    when(repository.findAllByUserIdOrderByRankAsc(USER_ID)).thenReturn(saved(LocalDateTime.now().minusHours(1), 3));

    assertEquals(3, store.findReusable(USER_ID, 2).orElseThrow().size());
  }

  @Test
  void doesNotReuseExpiredRecommendations() {
    when(repository.findAllByUserIdOrderByRankAsc(USER_ID)).thenReturn(saved(LocalDateTime.now().minusHours(25), 3));

    assertTrue(store.findReusable(USER_ID, 2).isEmpty());
  }

  @Test
  void doesNotReuseWhenFewerThanRequested() {
    when(repository.findAllByUserIdOrderByRankAsc(USER_ID)).thenReturn(saved(LocalDateTime.now(), 1));

    assertTrue(store.findReusable(USER_ID, 2).isEmpty());
  }

  @Test
  void doesNotReuseWhenNothingIsSaved() {
    when(repository.findAllByUserIdOrderByRankAsc(USER_ID)).thenReturn(List.of());

    assertTrue(store.findReusable(USER_ID, 2).isEmpty());
  }

  private static List<RecommendedGame> saved(LocalDateTime generatedAt, int count) {
    List<RecommendedGame> rows = new ArrayList<>();
    for (int i = 1; i <= count; i++) {
      rows.add(RecommendedGame.builder().userId(USER_ID).appId(i * 11L).rank(i).score(1.0 / i)
          .source("main").usedGameCount(2).generatedAt(generatedAt).build());
    }
    return rows;
  }
}
