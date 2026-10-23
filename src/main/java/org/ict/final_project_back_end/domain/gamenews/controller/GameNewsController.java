package org.ict.final_project_back_end.domain.gamenews.controller;

import org.ict.final_project_back_end.domain.gamenews.controller.docs.GameNewsApiDocs;
import org.ict.final_project_back_end.domain.gamenews.dto.GameNewsDetailResponse;
import org.ict.final_project_back_end.domain.gamenews.dto.GameNewsPageResponse;
import org.ict.final_project_back_end.domain.gamenews.service.GameNewsQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Public GET-only API under the existing games security rule. */
@RestController
@RequestMapping("/api/games")
public class GameNewsController implements GameNewsApiDocs {
  private final GameNewsQueryService service;
  public GameNewsController(GameNewsQueryService service) { this.service = service; }

  @Override @GetMapping("/news")
  public ResponseEntity<GameNewsPageResponse> list(
      @RequestParam(name = "appId", required = false) Long appId,
      @RequestParam(name = "category", required = false) String category,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size) {
    return ResponseEntity.ok(service.list(appId, category, page, size));
  }

  @Override @GetMapping("/{appId}/news")
  public ResponseEntity<GameNewsPageResponse> listForGame(@PathVariable("appId") long appId,
      @RequestParam(name = "category", required = false) String category,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size) {
    return ResponseEntity.ok(service.list(appId, category, page, size));
  }

  @Override @GetMapping("/{appId}/news/{newsId}")
  public ResponseEntity<GameNewsDetailResponse> detail(@PathVariable("appId") long appId, @PathVariable("newsId") long newsId) {
    return ResponseEntity.ok(service.detail(appId, newsId));
  }
}
