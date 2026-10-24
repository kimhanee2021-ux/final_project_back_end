package org.ict.final_project_back_end.domain.recommendation.service;

import org.ict.final_project_back_end.domain.library.entity.LibraryGame;
import org.ict.final_project_back_end.domain.recommendation.client.dto.ModelRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModelRequestFactoryTest {

  private final ModelRequestFactory factory = new ModelRequestFactory();

  @Test
  void playedGamesAreOldestFirstAndExcludeUnplayed() {
    List<LibraryGame> library = List.of(
        game(1, 120, LocalDateTime.of(2026, 1, 1, 0, 0)),
        game(2, 60, LocalDateTime.of(2025, 1, 1, 0, 0)),
        game(3, 0, null),
        game(4, 30, null));

    List<LibraryGame> played = factory.playedOldestFirst(library);

    assertEquals(List.of(4L, 2L, 1L), played.stream().map(LibraryGame::getAppId).toList()); // 기록 없는 것이 가장 오래된 것
  }

  @Test
  void convertsMinutesToHoursAndExcludesEveryOwnedGameIncludingUnplayed() {
    List<LibraryGame> library = List.of(game(1, 90, null), game(2, 0, null));

    ModelRequest request = factory.create(library, factory.playedOldestFirst(library), 10, 3);

    assertEquals(1.5, request.playedGames().get(0).playtimeHours());
    assertEquals(List.of(1L, 2L), request.excludeAppIds());
  }

  @Test
  void sendsOnlyTheMostRecentPlayedGames() {
    List<LibraryGame> library = playedGames(ModelRequestFactory.MAX_PLAYED_GAMES + 20);

    ModelRequest request = factory.create(library, factory.playedOldestFirst(library), 10, 0);

    assertEquals(ModelRequestFactory.MAX_PLAYED_GAMES, request.playedGames().size());
    assertEquals(21L, request.playedGames().get(0).appId()); // 앞의 20개(가장 오래된 것)가 잘렸다
    assertEquals(library.size(), request.excludeAppIds().size()); // 제외 목록은 자르지 않는다
  }

  @Test
  void newSlotsNeverExceedLimit() {
    List<LibraryGame> library = List.of(game(1, 60, null));

    ModelRequest request = factory.create(library, library, 2, 3);

    assertEquals(2, request.newSlots());
  }

  @Test
  void requestsExtraAndCapsExcludeListWhenOwnedExceedsModelLimit() {
    int owned = ModelRequestFactory.MODEL_MAX_HISTORY + 7;
    List<LibraryGame> library = playedGames(owned);

    ModelRequest request = factory.create(library, factory.playedOldestFirst(library), 10, 0);

    assertEquals(ModelRequestFactory.MODEL_MAX_HISTORY, request.excludeAppIds().size());
    assertEquals(17, request.limit()); // 걸러질 수 있는 초과분만큼 더 요청
  }

  @Test
  void extraRequestIsCappedAtModelMaxLimit() {
    List<LibraryGame> library = playedGames(ModelRequestFactory.MODEL_MAX_HISTORY + 100);

    ModelRequest request = factory.create(library, factory.playedOldestFirst(library), 10, 0);

    assertEquals(ModelRequestFactory.MODEL_MAX_LIMIT, request.limit());
  }

  // appId 1..count, 번호가 클수록 최근에 플레이
  private static List<LibraryGame> playedGames(int count) {
    List<LibraryGame> games = new ArrayList<>();
    IntStream.rangeClosed(1, count)
        .forEach(i -> games.add(game(i, 60, LocalDateTime.of(2020, 1, 1, 0, 0).plusMinutes(i))));
    return games;
  }

  private static LibraryGame game(long appId, int playtimeMinutes, LocalDateTime lastPlayedAt) {
    return LibraryGame.builder()
        .appId(appId).name("g" + appId).playtimeForever(playtimeMinutes).playtime2Weeks(0).lastPlayedAt(lastPlayedAt).build();
  }
}
