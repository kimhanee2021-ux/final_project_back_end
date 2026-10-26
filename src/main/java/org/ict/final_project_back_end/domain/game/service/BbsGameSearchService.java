package org.ict.final_project_back_end.domain.game.service;

import org.ict.final_project_back_end.domain.game.dto.BbsGameResponse;
import org.ict.final_project_back_end.domain.game.exception.GameNotFoundException;
import org.ict.final_project_back_end.domain.game.repository.GameRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BbsGameSearchService {
  private final GameRepository games;
  // 별명 테이블이 없는 DB에서는 게임 이름만으로 찾는다 (없는 테이블을 조회해 500이 나지 않게)

  public BbsGameSearchService(GameRepository games) { this.games = games; }

  public BbsGameResponse.Search search(String input, int limit) {
    String query = GameNameNormalizer.query(input);
    if (limit < 1 || limit > 20) throw GameSearchException.invalid("검색 개수는 1~20개로 지정해 주세요.");
    Long appId = null;
    if (query.matches("[0-9]{1,10}")) {
      long candidate = Long.parseLong(query);
      if (candidate > 0 && candidate <= 0xffffffffL) appId = candidate;
    }
    String pattern = "%" + GameNameNormalizer.escapeLike(query) + "%";
    var rows = games.searchForBbsByName(query, pattern, appId, limit + 1);
    return new BbsGameResponse.Search(query,
        rows.stream().limit(limit).map(BbsGameResponse::from).toList(), rows.size() > limit);
  }

  public BbsGameResponse find(long appId) {
    if (appId <= 0 || appId > 0xffffffffL) throw GameSearchException.invalid("올바른 Steam 게임 번호를 입력해 주세요.");
    return games.findForBbs(appId).map(BbsGameResponse::from).orElseThrow(GameNotFoundException::new);
  }

  public java.util.List<BbsGameResponse> exact(String input) {
    String query=GameNameNormalizer.query(input);
    Long appId=null;
    if(query.matches("[0-9]{1,10}")) {
      long number=Long.parseLong(query);if(number>0 && number<=0xffffffffL) appId=number;
    }
    var rows = games.findExactForBbsByName(query, appId);
    return rows.stream().map(BbsGameResponse::from).toList();
  }
}
