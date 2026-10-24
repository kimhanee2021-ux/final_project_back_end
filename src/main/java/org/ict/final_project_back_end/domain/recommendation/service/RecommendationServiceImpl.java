package org.ict.final_project_back_end.domain.recommendation.service;

import org.ict.final_project_back_end.domain.game.entity.Game;
import org.ict.final_project_back_end.domain.game.repository.GameRepository;
import org.ict.final_project_back_end.domain.library.entity.LibraryGame;
import org.ict.final_project_back_end.domain.library.repository.LibraryGameRepository;
import org.ict.final_project_back_end.domain.recommendation.client.RecommendationModelClient;
import org.ict.final_project_back_end.domain.recommendation.client.dto.ModelResult;
import org.ict.final_project_back_end.domain.recommendation.dto.RecommendationResponse;
import org.ict.final_project_back_end.domain.recommendation.entity.RecommendedGame;
import org.ict.final_project_back_end.domain.recommendation.exception.NoPlayedGamesException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

// 모델 서버 호출이 느릴 수 있어 서비스 전체에는 트랜잭션을 걸지 않는다. 저장은 RecommendationStore가 짧게 처리한다.
@Service
public class RecommendationServiceImpl implements RecommendationService {

  private final LibraryGameRepository libraryGameRepository;
  private final GameRepository gameRepository;
  private final RecommendationStore store;
  private final RecommendationModelClient modelClient;
  private final ModelRequestFactory requestFactory;
  private final int newSlots;

  public RecommendationServiceImpl(LibraryGameRepository libraryGameRepository,
                                   GameRepository gameRepository,
                                   RecommendationStore store,
                                   RecommendationModelClient modelClient,
                                   ModelRequestFactory requestFactory,
                                   @Value("${app.recommend.new-slots}") int newSlots) {
    this.libraryGameRepository = libraryGameRepository;
    this.gameRepository = gameRepository;
    this.store = store;
    this.modelClient = modelClient;
    this.requestFactory = requestFactory;
    this.newSlots = newSlots;
  }

  @Override
  public RecommendationResponse getRecommendations(Long userId, int limit) {
    List<LibraryGame> library = libraryGameRepository.findAllByUserId(userId);
    List<LibraryGame> played = requestFactory.playedOldestFirst(library);
    if (played.isEmpty()) {
      throw new NoPlayedGamesException();
    }

    List<RecommendedGame> recommendations = store.findReusable(userId, limit)
        .orElseGet(() -> recommendAgain(userId, library, played, limit));
    return toResponse(recommendations, limit, library.size() - played.size());
  }

  private List<RecommendedGame> recommendAgain(Long userId, List<LibraryGame> library,
                                               List<LibraryGame> played, int limit) {
    ModelResult result = modelClient.recommend(requestFactory.create(library, played, limit, newSlots));
    List<ModelResult.Item> items = withoutOwned(result.items(), library, limit);

    int usedGameCount = result.usedAppIds() == null ? 0 : result.usedAppIds().size();
    LocalDateTime now = LocalDateTime.now();
    List<RecommendedGame> fresh = IntStream.range(0, items.size())
        .mapToObj(i -> RecommendedGame.builder()
            .userId(userId)
            .appId(items.get(i).appId())
            .rank(i + 1)
            .score(items.get(i).score())
            .source(items.get(i).source())
            .usedGameCount(usedGameCount)
            .generatedAt(now)
            .build())
        .toList();
    store.replace(userId, fresh);
    return fresh;
  }

  // 모델 서버가 제외 목록 한도 때문에 못 거른 보유 게임까지 걸러내고 limit개로 맞춘다 (한도 안이면 걸러지는 것이 없다)
  private List<ModelResult.Item> withoutOwned(List<ModelResult.Item> items, List<LibraryGame> library, int limit) {
    Set<Long> owned = library.stream().map(LibraryGame::getAppId).collect(Collectors.toSet());
    return items.stream().filter(item -> !owned.contains(item.appId())).limit(limit).toList();
  }

  private RecommendationResponse toResponse(List<RecommendedGame> recommendations, int limit, int unplayedGameCount) {
    List<RecommendedGame> top = recommendations.stream().limit(limit).toList();
    Map<Long, Game> gamesByAppId = gameRepository.findAllById(top.stream().map(RecommendedGame::getAppId).toList())
        .stream()
        .collect(Collectors.toMap(Game::getAppId, Function.identity()));
    return RecommendationResponse.of(top, gamesByAppId, unplayedGameCount);
  }
}
