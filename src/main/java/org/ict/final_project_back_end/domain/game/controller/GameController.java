package org.ict.final_project_back_end.domain.game.controller;

import org.ict.final_project_back_end.domain.game.controller.docs.GameApiDocs;
import org.ict.final_project_back_end.domain.game.dto.GameDetailResponse;
import org.ict.final_project_back_end.domain.game.dto.GamePageResponse;
import org.ict.final_project_back_end.domain.game.service.GameService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/games")
@Validated
public class GameController implements GameApiDocs {
  private final GameService service;

  public GameController(GameService service) {
    this.service = service;
  }

  @Override
  @GetMapping
  public ResponseEntity<GamePageResponse> list(
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size,
      @RequestParam(name = "genre", required = false) String genre) {
    return ResponseEntity.ok(service.listGames(page, size, genre));
  }

  @Override
  @GetMapping("/search")
  public ResponseEntity<GamePageResponse> search(
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size,
      @RequestParam(name = "name") String name) {
    return ResponseEntity.ok(service.searchGames(page, size, name));
  }

  @Override
  @GetMapping("/{appId}")
  public ResponseEntity<GameDetailResponse> detail(@PathVariable("appId") Long appId) {
    return ResponseEntity.ok(service.getGame(appId));
  }
}
