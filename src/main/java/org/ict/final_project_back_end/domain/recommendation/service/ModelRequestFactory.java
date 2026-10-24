package org.ict.final_project_back_end.domain.recommendation.service;

import org.ict.final_project_back_end.domain.library.entity.LibraryGame;
import org.ict.final_project_back_end.domain.recommendation.client.dto.ModelRequest;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

// 보유 게임 목록을 추천 모델 서버 요청으로 바꾼다. 모델 서버의 요청 크기 한도(5000개, limit 50)를 넘지 않게 맞추는 일도 여기서 한다.
@Component
public class ModelRequestFactory {

  private static final double MINUTES_PER_HOUR = 60.0;
  // 모델은 모르는 게임을 빼고 최근 50개만 쓴다. 모르는 게임이 섞여도 50개가 채워지도록 여유를 두고 보낸다.
  static final int MAX_PLAYED_GAMES = 100;
  // 모델 서버가 한 번에 받는 played_games / exclude_app_ids 최대 길이와 limit 최대값
  static final int MODEL_MAX_HISTORY = 5000;
  static final int MODEL_MAX_LIMIT = 50;

  // 플레이한 게임만, 오래된 순 → 최근 순. 마지막 플레이 기록이 없으면 가장 오래된 것으로 본다.
  public List<LibraryGame> playedOldestFirst(List<LibraryGame> library) {
    return library.stream()
        .filter(game -> game.getPlaytimeForever() > 0)
        .sorted(Comparator.comparing(LibraryGame::getLastPlayedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
        .toList();
  }

  // 보유 게임은 미플레이 포함 전부 제외 목록에 담는다. 한도를 넘는 만큼은 모델이 걸러주지 못하므로
  // 그만큼 더 요청해 두고, 호출한 쪽이 응답에서 보유 게임을 직접 거른다.
  public ModelRequest create(List<LibraryGame> library, List<LibraryGame> played, int limit, int newSlots) {
    List<LibraryGame> recentPlayed = played.subList(Math.max(0, played.size() - MAX_PLAYED_GAMES), played.size());
    List<ModelRequest.PlayedGame> history = recentPlayed.stream()
        .map(game -> new ModelRequest.PlayedGame(game.getAppId(), game.getPlaytimeForever() / MINUTES_PER_HOUR))
        .toList();
    List<Long> excluded = library.stream().map(LibraryGame::getAppId).limit(MODEL_MAX_HISTORY).toList();

    int overflow = library.size() - excluded.size();
    int requestedLimit = Math.min(MODEL_MAX_LIMIT, limit + overflow);
    return new ModelRequest(history, excluded, requestedLimit, Math.min(newSlots, requestedLimit));
  }
}
