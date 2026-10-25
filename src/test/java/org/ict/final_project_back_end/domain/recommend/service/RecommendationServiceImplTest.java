package org.ict.final_project_back_end.domain.recommend.service;

import org.ict.final_project_back_end.domain.game.entity.Game;
import org.ict.final_project_back_end.domain.game.repository.GameRepository;
import org.ict.final_project_back_end.domain.library.entity.LibraryGame;
import org.ict.final_project_back_end.domain.library.repository.LibraryGameRepository;
import org.ict.final_project_back_end.domain.recommend.client.RecommendModelClient;
import org.ict.final_project_back_end.domain.recommend.dto.RecommendationResponse;
import org.ict.final_project_back_end.domain.recommend.exception.RecommendException;
import org.ict.final_project_back_end.global.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class RecommendationServiceImplTest {

  private final LibraryGameRepository library = mock(LibraryGameRepository.class);
  private final GameRepository games = mock(GameRepository.class);
  private final RecommendModelClient model = mock(RecommendModelClient.class);
  private final RecommendationServiceImpl service = new RecommendationServiceImpl(library, games, model);

  private static LibraryGame owned(long appId, int minutes, LocalDateTime lastPlayed) {
    return LibraryGame.builder().appId(appId).name("g" + appId).playtimeForever(minutes).playtime2Weeks(0)
        .lastPlayedAt(lastPlayed).build();
  }

  @Test
  void sendsPlayedGamesOldestFirstExcludesAllOwnedAndAttachesGameInfo() {
    LocalDateTime now = LocalDateTime.of(2026, 10, 25, 12, 0);
    when(library.findAllByUserId(1L)).thenReturn(List.of(
        owned(10, 120, now),               // 가장 최근
        owned(20, 0, null),                // 플레이 안 함 -> 이력 제외, 추천 제외
        owned(30, 30, now.minusDays(3)),
        owned(40, 60, null)));             // 플레이 기록 시각 없음 -> 가장 오래된 쪽
    when(model.recommend(any())).thenReturn(new RecommendModelClient.Response(
        List.of(new RecommendModelClient.Item(100, 0.9, "main"), new RecommendModelClient.Item(200, 0.5, "new")),
        List.of(10L, 30L, 40L), List.of()));
    when(games.findAllById(anyList())).thenReturn(List.of(
        Game.builder().appId(100L).name("추천 게임").genre("Action,RPG").thumbnailUrl("http://img/100").build()));

    RecommendationResponse response = service.recommendForUser(1L, 5, 2);

    ArgumentCaptor<RecommendModelClient.Request> sent = ArgumentCaptor.forClass(RecommendModelClient.Request.class);
    verify(model).recommend(sent.capture());
    assertEquals(List.of(40L, 30L, 10L), sent.getValue().playedGames().stream().map(RecommendModelClient.PlayedGame::appId).toList());
    assertEquals(2.0, sent.getValue().playedGames().get(2).playtimeHours());
    assertEquals(List.of(10L, 20L, 30L, 40L), sent.getValue().excludeAppIds());
    assertEquals(5, sent.getValue().limit());
    assertEquals(2, sent.getValue().newSlots());

    assertEquals(2, response.recommendations().size());
    RecommendationResponse.Item first = response.recommendations().getFirst();
    assertEquals(1, first.rank());
    assertEquals("추천 게임", first.game().name());
    assertEquals("http://img/100", first.game().thumbnailUrl());
    RecommendationResponse.Item second = response.recommendations().get(1);
    assertEquals(2, second.rank());
    assertEquals("new", second.source());
    assertNull(second.game());
    assertEquals(3, response.meta().usedGameCount());
    assertEquals(1, response.meta().unplayedGameCount());
  }

  @Test
  void noPlayedGamesNeverCallsModel() {
    when(library.findAllByUserId(1L)).thenReturn(List.of(owned(20, 0, null)));

    RecommendException e = assertThrows(RecommendException.class, () -> service.recommendForUser(1L, 10, 3));
    assertEquals("NO_PLAYED_GAMES", e.getCode());
    verifyNoInteractions(model);
  }

  @Test
  void rejectsOutOfRangeLimits() {
    assertThrows(InvalidRequestException.class, () -> service.recommendForUser(1L, 0, 0));
    assertThrows(InvalidRequestException.class, () -> service.recommendForUser(1L, 51, 0));
    assertThrows(InvalidRequestException.class, () -> service.recommendForUser(1L, 10, -1));
    verifyNoInteractions(library, model);
  }
}
