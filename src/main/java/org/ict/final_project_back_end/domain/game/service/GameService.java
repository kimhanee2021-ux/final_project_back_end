package org.ict.final_project_back_end.domain.game.service;

import org.ict.final_project_back_end.domain.game.dto.GameCursorPageResponse;
import org.ict.final_project_back_end.domain.game.dto.GameDetailResponse;
import org.ict.final_project_back_end.domain.game.dto.GamePageResponse;
import org.ict.final_project_back_end.domain.game.dto.GameResponse;
import org.ict.final_project_back_end.domain.game.dto.GameSyncStatusResponse;

// 저장된 게임 목록 조회·검색과 상세정보 조회를 담당한다.
public interface GameService {

  // appId 오름차순으로 페이지 조회한다
  GamePageResponse<GameResponse> getGames(int page, int size);

  GamePageResponse<GameResponse> searchGames(String keyword, int page, int size);

  // afterAppId보다 큰 appId부터 오름차순으로 가져온다. 처음부터면 null을 넘긴다.
  GameCursorPageResponse getGamesAfter(Long afterAppId, int size);

  // 전체 동기화가 지금 어디까지 왔는지 조회한다
  GameSyncStatusResponse getSyncStatus();

  // 상세정보는 하루 동안 DB에 캐시하고, 만료되었으면 스팀 스토어에서 다시 가져온다
  GameDetailResponse getGameDetail(Long appId);

  // 시드용: 스토어에서 상세정보를 가져와 출시된 게임이면 캐시에 저장하고 true를 돌려준다.
  // 출시 예정이면 아무것도 저장하지 않고 false를 돌려준다. games 행은 만들지 않는다.
  boolean cacheIfReleased(Long appId);
}
