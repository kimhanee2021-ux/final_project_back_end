package org.ict.final_project_back_end.domain.game.controller;

import org.ict.final_project_back_end.domain.game.dto.GameCursorPageResponse;
import org.ict.final_project_back_end.domain.game.dto.GameDetailResponse;
import org.ict.final_project_back_end.domain.game.dto.GamePageResponse;
import org.ict.final_project_back_end.domain.game.dto.GameResponse;
import org.ict.final_project_back_end.domain.game.dto.GameSyncStatusResponse;
import org.ict.final_project_back_end.domain.game.service.GameService;
import org.ict.final_project_back_end.global.swagger.GameApiDocs;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 게임 목록·검색·상세 API. 실제 로직은 GameService에 위임하고 HTTP 매핑만 담당한다.
@RestController
@RequestMapping("/api/games")
public class GameController implements GameApiDocs {

  private final GameService gameService;

  public GameController(GameService gameService) {
    this.gameService = gameService;
  }

  @Override
  @GetMapping
  public ResponseEntity<GamePageResponse<GameResponse>> getGames(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok(gameService.getGames(page, size));
  }

  @Override
  @GetMapping("/search")
  public ResponseEntity<GamePageResponse<GameResponse>> searchGames(
      @RequestParam String keyword,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok(gameService.searchGames(keyword, page, size));
  }

  // 전체 데이터를 appId 오름차순으로 끝까지 받을 때 쓴다. 첫 요청은 afterAppId 없이, 이후에는 응답의 nextAfterAppId를 넘긴다.
  @Override
  @GetMapping("/all")
  public ResponseEntity<GameCursorPageResponse> getAllGames(
      @RequestParam(required = false) Long afterAppId,
      @RequestParam(defaultValue = "100") int size) {
    return ResponseEntity.ok(gameService.getGamesAfter(afterAppId, size));
  }

  @Override
  @GetMapping("/sync-status")
  public ResponseEntity<GameSyncStatusResponse> getSyncStatus() {
    return ResponseEntity.ok(gameService.getSyncStatus());
  }

  @Override
  @GetMapping("/{appId}")
  public ResponseEntity<GameDetailResponse> getGameDetail(@PathVariable Long appId) {
    return ResponseEntity.ok(gameService.getGameDetail(appId));
  }
}
