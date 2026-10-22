package org.ict.final_project_back_end.domain.game.service;

import org.ict.final_project_back_end.domain.game.dto.FeaturedCategoryResponse;
import org.ict.final_project_back_end.domain.game.dto.FeaturedGameItemResponse;
import org.ict.final_project_back_end.domain.game.entity.FeaturedCategoryGame;
import org.ict.final_project_back_end.domain.game.entity.FeaturedGame;
import org.ict.final_project_back_end.domain.game.repository.FeaturedCategoryGameRepository;
import org.ict.final_project_back_end.domain.game.repository.FeaturedGameRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 캐시 테이블(featured_games, featured_category_games)을 읽어 응답 DTO로 바꾸기만 한다.
// 갱신(스팀 재조회)은 FeaturedGameSyncService가 매일 스케줄로 처리하므로 여기서는 조회만 한다.
// 게임 카드의 대표 태그는 games 카탈로그에서 GameTagResolver가 찾아 붙인다.
@Service
@Transactional(readOnly = true)
public class FeaturedGameServiceImpl implements FeaturedGameService {

  private final FeaturedGameRepository featuredGameRepository;
  private final FeaturedCategoryGameRepository featuredCategoryGameRepository;
  private final GameTagResolver gameTagResolver;

  public FeaturedGameServiceImpl(FeaturedGameRepository featuredGameRepository,
      FeaturedCategoryGameRepository featuredCategoryGameRepository, GameTagResolver gameTagResolver) {
    this.featuredGameRepository = featuredGameRepository;
    this.featuredCategoryGameRepository = featuredCategoryGameRepository;
    this.gameTagResolver = gameTagResolver;
  }

  @Override
  public List<FeaturedGameItemResponse> getFeatured() {
    List<FeaturedGame> games = featuredGameRepository.findAllByOrderByRankOrderAsc();
    Map<Long, List<String>> tags = gameTagResolver.resolve(games.stream().map(FeaturedGame::getAppId).toList());
    return games.stream()
        .map(game -> FeaturedGameItemResponse.from(game, tags.getOrDefault(game.getAppId(), List.of())))
        .toList();
  }

  @Override
  public List<FeaturedCategoryResponse> getFeaturedCategories() {
    List<FeaturedCategoryGame> games = featuredCategoryGameRepository.findAllByOrderByCategoryOrderAscRankOrderAsc();
    // 카테고리마다 같은 게임이 겹칠 수 있어, 태그는 전체 appId를 한 번에 조회한다.
    Map<Long, List<String>> tags = gameTagResolver.resolve(games.stream().map(FeaturedCategoryGame::getAppId).toList());

    // 카테고리 이름은 카테고리당 하나뿐이라 키별로 (이름, 아이템 목록)만 따로 쌓았다가 마지막에 DTO로 합친다.
    Map<String, String> categoryNames = new LinkedHashMap<>();
    Map<String, List<FeaturedGameItemResponse>> categoryItems = new LinkedHashMap<>();
    for (FeaturedCategoryGame game : games) {
      categoryNames.putIfAbsent(game.getCategoryKey(), game.getCategoryName());
      categoryItems.computeIfAbsent(game.getCategoryKey(), k -> new ArrayList<>())
          .add(FeaturedGameItemResponse.from(game, tags.getOrDefault(game.getAppId(), List.of())));
    }
    return categoryNames.entrySet().stream()
        .map(entry -> new FeaturedCategoryResponse(entry.getKey(), entry.getValue(),
            categoryItems.get(entry.getKey())))
        .toList();
  }
}
