package org.ict.final_project_back_end.domain.game.service;

import org.ict.final_project_back_end.domain.game.dto.GameDetailResponse;
import org.ict.final_project_back_end.domain.game.dto.GamePageResponse;
import org.ict.final_project_back_end.domain.game.dto.GameSummaryResponse;
import org.ict.final_project_back_end.domain.game.entity.Game;
import org.ict.final_project_back_end.domain.game.repository.GameRepository;
import org.ict.final_project_back_end.domain.game.repository.GameSpecifications;
import org.ict.final_project_back_end.global.exception.GameNotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GameServiceImpl implements GameService {
  private final GameRepository games;

  public GameServiceImpl(GameRepository games) {
    this.games = games;
  }

  @Override
  public GamePageResponse listGames(int page, int size, String genre) {
    return findPage(GameSpecifications.matching(genre, null), page, size);
  }

  @Override
  public GamePageResponse searchGames(int page, int size, String name) {
    return findPage(GameSpecifications.matching(null, name), page, size);
  }

  @Override
  public GameDetailResponse getGame(Long appId) {
    return games.findById(appId)
        .map(GameDetailResponse::from)
        .orElseThrow(GameNotFoundException::new);
  }

  // 목록과 검색이 같은 정렬·변환 규칙을 쓰도록 한 곳으로 모았다.
  // games 테이블에 출시일 컬럼이 없어 appId(수집 순서) 내림차순으로 임시 대체한다.
  private GamePageResponse findPage(Specification<Game> condition, int page, int size) {
    var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "appId"));
    var result = games.findAll(condition, pageable).map(GameSummaryResponse::from);
    return GamePageResponse.from(result);
  }
}
