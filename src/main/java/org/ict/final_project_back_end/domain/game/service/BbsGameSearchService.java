package org.ict.final_project_back_end.domain.game.service;

import org.ict.final_project_back_end.domain.game.dto.BbsGameResponse;
import org.ict.final_project_back_end.domain.game.exception.GameNotFoundException;
import org.ict.final_project_back_end.domain.game.repository.GameRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BbsGameSearchService {
  private final GameRepository games;

  public BbsGameSearchService(GameRepository games) { this.games = games; }

  public BbsGameResponse.Search search(String input, int limit) {
    String query = GameNameNormalizer.query(input);
    if (limit < 1 || limit > 20) throw GameSearchException.invalid("검색 개수는 1~20개로 지정해 주세요.");
    Long appId = null;
    if (query.matches("[0-9]{1,10}")) {
      long candidate = Long.parseLong(query);
      if (candidate > 0 && candidate <= 0xffffffffL) appId = candidate;
    }
    var rows = games.searchForBbs(query, "%" + GameNameNormalizer.escapeLike(query) + "%",
        appId, PageRequest.of(0, limit + 1));
    return new BbsGameResponse.Search(query,
        rows.stream().limit(limit).map(BbsGameResponse::from).toList(), rows.size() > limit);
  }

  public BbsGameResponse find(long appId) {
    if (appId <= 0 || appId > 0xffffffffL) throw GameSearchException.invalid("올바른 Steam 게임 번호를 입력해 주세요.");
    return games.findForBbs(appId).map(BbsGameResponse::from).orElseThrow(GameNotFoundException::new);
  }
}
