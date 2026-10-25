package org.ict.final_project_back_end.domain.game.service;

import org.ict.final_project_back_end.domain.game.dto.GameDetailResponse;
import org.ict.final_project_back_end.domain.game.dto.GamePageResponse;
import org.ict.final_project_back_end.domain.game.dto.GameSummaryResponse;
import org.ict.final_project_back_end.domain.game.exception.GameNotFoundException;
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

  // games 테이블에 출시일 컬럼이 없어 appId(수집 순서) 내림차순으로 임시 대체한다.
  @Override
  public GamePageResponse listGames(int page, int size, String genre) {
    var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "appId"));
    var result = games.findAll(GameSpecifications.genreContains(genre), pageable).map(GameSummaryResponse::from);
    return GamePageResponse.from(result);
  }

  @Override
  public GameDetailResponse getGame(Long appId) {
    return games.findById(appId)
        .map(GameDetailResponse::from)
        .orElseThrow(GameNotFoundException::new);
  }
}
