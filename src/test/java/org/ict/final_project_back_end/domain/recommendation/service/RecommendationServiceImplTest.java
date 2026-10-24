package org.ict.final_project_back_end.domain.recommendation.service;

import org.ict.final_project_back_end.domain.game.entity.Game;
import org.ict.final_project_back_end.domain.game.repository.GameRepository;
import org.ict.final_project_back_end.domain.library.entity.LibraryGame;
import org.ict.final_project_back_end.domain.library.repository.LibraryGameRepository;
import org.ict.final_project_back_end.domain.recommendation.client.RecommendationModelClient;
import org.ict.final_project_back_end.domain.recommendation.client.dto.ModelRequest;
import org.ict.final_project_back_end.domain.recommendation.client.dto.ModelResult;
import org.ict.final_project_back_end.domain.recommendation.dto.RecommendationResponse;
import org.ict.final_project_back_end.domain.recommendation.entity.RecommendedGame;
import org.ict.final_project_back_end.domain.recommendation.exception.NoPlayedGamesException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// DB와 모델 서버 없이 추천 흐름(저장본 재사용 / 새로 계산 / 요청 구성)만 검증한다
class RecommendationServiceImplTest {

  private static final long USER_ID = 5L;

  private final LibraryGameRepository libraryRepository = mock(LibraryGameRepository.class);
  private final GameRepository gameRepository = mock(GameRepository.class);
  private final RecommendationStore store = mock(RecommendationStore.class);
  private final FakeModelClient model = new FakeModelClient();

  private RecommendationServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new RecommendationServiceImpl(
        libraryRepository, gameRepository, store, model, new ModelRequestFactory(), 3);
    when(libraryRepository.findAllByUserId(USER_ID)).thenReturn(List.of(
        library(100, 120, LocalDateTime.of(2026, 1, 1, 0, 0)),
        library(200, 60, LocalDateTime.of(2025, 1, 1, 0, 0)),
        library(300, 0, null)));
    when(store.findReusable(anyLong(), anyInt())).thenReturn(Optional.empty());
    when(gameRepository.findAllById(anyList())).thenReturn(List.of());
  }

  @Test
  void sendsPlayedGamesOldestFirstInHoursAndExcludesEveryOwnedGame() {
    service.getRecommendations(USER_ID, 2);

    ModelRequest request = model.requests.get(0);
    assertEquals(List.of(200L, 100L), request.playedGames().stream().map(ModelRequest.PlayedGame::appId).toList());
    assertEquals(List.of(1.0, 2.0), request.playedGames().stream().map(ModelRequest.PlayedGame::playtimeHours).toList());
    assertEquals(List.of(100L, 200L, 300L), request.excludeAppIds());
    assertEquals(2, request.limit());
    assertEquals(2, request.newSlots()); // 신작 칸은 limit을 넘지 않는다
  }

  @Test
  void savesNewResultAndBuildsResponseWithGameInfoAndMeta() {
    when(gameRepository.findAllById(anyList())).thenReturn(List.of(game(11L, "Game 11")));

    RecommendationResponse response = service.getRecommendations(USER_ID, 2);

    verify(store).replace(any(), anyList());
    assertEquals(2, response.recommendations().size());
    assertEquals(1, response.recommendations().get(0).rank());
    assertEquals("Game 11", response.recommendations().get(0).game().name());
    assertNull(response.recommendations().get(1).game()); // 카탈로그에 없는 게임
    assertEquals("new", response.recommendations().get(1).source());
    assertEquals(2, response.meta().usedGameCount());
    assertEquals(1, response.meta().unplayedGameCount());
  }

  @Test
  void reusesFreshSavedRecommendationsWithoutCallingTheModel() {
    when(store.findReusable(USER_ID, 2)).thenReturn(Optional.of(saved(LocalDateTime.now().minusHours(1), 3)));

    RecommendationResponse response = service.getRecommendations(USER_ID, 2);

    assertTrue(model.requests.isEmpty());
    verify(store, never()).replace(anyLong(), anyList());
    assertEquals(List.of(11L, 22L), response.recommendations().stream().map(r -> r.appId()).toList());
  }

  @Test
  void dropsOwnedGamesTheModelCouldNotExcludeAndKeepsLimit() {
    model.items = List.of(
        new ModelResult.Item(100L, 0.9, "main"), // 보유 게임
        new ModelResult.Item(11L, 0.8, "main"),
        new ModelResult.Item(22L, 0.7, "main"),
        new ModelResult.Item(33L, 0.6, "main"));

    RecommendationResponse response = service.getRecommendations(USER_ID, 2);

    assertEquals(List.of(11L, 22L), response.recommendations().stream().map(r -> r.appId()).toList());
    assertEquals(List.of(1, 2), response.recommendations().stream().map(r -> r.rank()).toList()); // 순위는 거른 뒤 다시 매긴다
  }

  @Test
  void throwsNoPlayedGamesWhenNothingWasPlayed() {
    when(libraryRepository.findAllByUserId(USER_ID)).thenReturn(List.of(library(300, 0, null)));

    assertThrows(NoPlayedGamesException.class, () -> service.getRecommendations(USER_ID, 2));
    assertTrue(model.requests.isEmpty());
  }

  private static LibraryGame library(long appId, int playtimeMinutes, LocalDateTime lastPlayedAt) {
    return LibraryGame.builder()
        .appId(appId).name("g" + appId).playtimeForever(playtimeMinutes).playtime2Weeks(0).lastPlayedAt(lastPlayedAt).build();
  }

  private static Game game(long appId, String name) {
    return Game.builder().appId(appId).name(name).genre("RPG").thumbnailUrl("http://img/" + appId).build();
  }

  private static List<RecommendedGame> saved(LocalDateTime generatedAt, int count) {
    List<RecommendedGame> rows = new ArrayList<>();
    for (int i = 1; i <= count; i++) {
      rows.add(RecommendedGame.builder().userId(USER_ID).appId(i * 11L).rank(i).score(1.0 / i)
          .source("main").usedGameCount(2).generatedAt(generatedAt).build());
    }
    return rows;
  }

  private static class FakeModelClient implements RecommendationModelClient {
    final List<ModelRequest> requests = new ArrayList<>();
    List<ModelResult.Item> items = List.of(new ModelResult.Item(11L, 0.9, "main"), new ModelResult.Item(22L, 0.5, "new"));

    @Override
    public ModelResult recommend(ModelRequest request) {
      requests.add(request);
      return new ModelResult(
          items,
          List.of(200L, 100L),
          List.of());
    }
  }
}
