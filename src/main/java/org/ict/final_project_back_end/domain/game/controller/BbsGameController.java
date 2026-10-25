package org.ict.final_project_back_end.domain.game.controller;

import org.ict.final_project_back_end.domain.game.dto.BbsGameResponse;
import org.ict.final_project_back_end.domain.game.service.BbsGameSearchService;
import org.ict.final_project_back_end.global.exception.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@RequestMapping("/api/games/bbs")
public class BbsGameController {
  private final BbsGameSearchService search;
  public BbsGameController(BbsGameSearchService search) { this.search = search; }

  @GetMapping("/search")
  public BbsGameResponse.Search search(@RequestParam("q") String query,
      @RequestParam(name = "limit", defaultValue = "12") int limit) {
    return search.search(query, limit);
  }

  @GetMapping("/{appId}")
  public BbsGameResponse game(@PathVariable("appId") long appId) { return search.find(appId); }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> invalidNumber() {
    return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_GAME_SEARCH", "게임 번호와 검색 개수를 확인해 주세요."));
  }
}
