package org.ict.final_project_back_end.domain.game.service;

import org.ict.final_project_back_end.domain.game.dto.GamePageResponse;
import org.ict.final_project_back_end.domain.game.dto.GameSummaryResponse;
import org.ict.final_project_back_end.domain.game.repository.GameRepository;
import org.ict.final_project_back_end.domain.game.repository.GameSpecifications;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
    // games 테이블에 출시일 컬럼이 없어 appId(수집 순서) 내림차순으로 임시 대체.
    var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "appId"));
    var result = games.findAll(GameSpecifications.matching(genre, null), pageable)
        .map(GameSummaryResponse::from);
    return GamePageResponse.from(result);
  }

  @Override
  public GamePageResponse searchGames(int page, int size, String name) {
    var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "appId"));
    var result = games.findAll(GameSpecifications.matching(null, name), pageable)
        .map(GameSummaryResponse::from);
    return GamePageResponse.from(result);
  }
}
