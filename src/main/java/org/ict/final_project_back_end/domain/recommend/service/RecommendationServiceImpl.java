package org.ict.final_project_back_end.domain.recommend.service;

import org.ict.final_project_back_end.domain.game.entity.Game;
import org.ict.final_project_back_end.domain.game.repository.GameRepository;
import org.ict.final_project_back_end.domain.library.entity.LibraryGame;
import org.ict.final_project_back_end.domain.library.repository.LibraryGameRepository;
import org.ict.final_project_back_end.domain.recommend.client.RecommendModelClient;
import org.ict.final_project_back_end.domain.recommend.dto.RecommendationResponse;
import org.ict.final_project_back_end.domain.recommend.exception.RecommendException;
import org.ict.final_project_back_end.global.exception.InvalidRequestException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

// 추천 서버 호출 중에는 DB 커넥션을 잡고 있지 않도록 트랜잭션을 걸지 않는다 (조회는 저장소 호출마다 끝난다)
@Service
public class RecommendationServiceImpl implements RecommendationService {

  // 추천 서버가 한 요청에 받는 이력/제외 목록 최대 길이 (FastAPI MAX_HISTORY와 같다)
  private static final int MAX_HISTORY = 5000;

  // 플레이 기록이 없는 게임을 가장 오래된 쪽에 두고, 나머지는 마지막 플레이 시각 순 (오래된 순 -> 최근 순)
  private static final Comparator<LibraryGame> OLDEST_FIRST = Comparator.comparing(
      LibraryGame::getLastPlayedAt, Comparator.nullsFirst(Comparator.<LocalDateTime>naturalOrder()));

  private final LibraryGameRepository libraryGameRepository;
  private final GameRepository gameRepository;
  private final RecommendModelClient modelClient;

  public RecommendationServiceImpl(LibraryGameRepository libraryGameRepository, GameRepository gameRepository,
                                   RecommendModelClient modelClient) {
    this.libraryGameRepository = libraryGameRepository;
    this.gameRepository = gameRepository;
    this.modelClient = modelClient;
  }

  @Override
  public RecommendationResponse recommendForUser(Long userId, int limit, int newSlots) {
    if (limit < 1 || limit > MAX_LIMIT) {
      throw new InvalidRequestException("limit은 1~" + MAX_LIMIT + " 사이여야 합니다");
    }
    if (newSlots < 0 || newSlots > MAX_LIMIT) {
      throw new InvalidRequestException("newSlots는 0~" + MAX_LIMIT + " 사이여야 합니다");
    }

    List<LibraryGame> owned = libraryGameRepository.findAllByUserId(userId);
    // 플레이 시간이 0인 게임은 취향 계산에서 빼지만, 이미 가진 게임이라 추천에서는 제외한다
    List<LibraryGame> played = owned.stream()
        .filter(g -> g.getPlaytimeForever() != null && g.getPlaytimeForever() > 0)
        .sorted(OLDEST_FIRST)
        .toList();
    if (played.isEmpty()) {
      throw RecommendException.noPlayedGames();
    }

    List<RecommendModelClient.PlayedGame> history = latest(played).stream()
        .map(g -> new RecommendModelClient.PlayedGame(g.getAppId(), g.getPlaytimeForever() / 60.0))
        .toList();
    List<Long> exclude = owned.stream().map(LibraryGame::getAppId).distinct().limit(MAX_HISTORY).toList();

    RecommendModelClient.Response result = modelClient.recommend(
        new RecommendModelClient.Request(history, exclude, limit, newSlots));

    Map<Long, Game> games = gameRepository.findAllById(
            result.items().stream().map(RecommendModelClient.Item::appId).toList())
        .stream()
        .collect(Collectors.toMap(Game::getAppId, Function.identity()));

    List<RecommendationResponse.Item> items = IntStream.range(0, result.items().size())
        .mapToObj(i -> toItem(i + 1, result.items().get(i), games.get(result.items().get(i).appId())))
        .toList();
    int used = result.usedAppIds() == null ? 0 : result.usedAppIds().size();
    return new RecommendationResponse(items, new RecommendationResponse.Meta(used, owned.size() - played.size()));
  }

  // 이력이 너무 길면 최근 게임만 남긴다 (모델도 최근 이력부터 쓴다)
  private static List<LibraryGame> latest(List<LibraryGame> played) {
    return played.size() <= MAX_HISTORY ? played : played.subList(played.size() - MAX_HISTORY, played.size());
  }

  private static RecommendationResponse.Item toItem(int rank, RecommendModelClient.Item item, Game game) {
    RecommendationResponse.GameInfo info = game == null ? null
        : new RecommendationResponse.GameInfo(game.getName(), game.getGenre(), game.getThumbnailUrl());
    return new RecommendationResponse.Item(rank, item.appId(), item.score(), item.source(), info);
  }
}
