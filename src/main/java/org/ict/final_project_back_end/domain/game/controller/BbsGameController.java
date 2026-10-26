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

  @GetMapping("/{appId}")
  public BbsGameResponse game(@PathVariable("appId") long appId) { return search.find(appId); }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> invalidNumber() {
    return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_GAME_SEARCH", "게임 번호를 확인해 주세요."));
  }
}
