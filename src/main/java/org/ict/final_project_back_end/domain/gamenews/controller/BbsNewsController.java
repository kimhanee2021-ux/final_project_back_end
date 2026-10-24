package org.ict.final_project_back_end.domain.gamenews.controller;

import org.ict.final_project_back_end.domain.gamenews.dto.BbsNewsResponse;
import org.ict.final_project_back_end.domain.gamenews.service.BbsOfficialNewsService;
import org.ict.final_project_back_end.global.exception.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@RequestMapping("/api/games/bbs")
public class BbsNewsController {
  private final BbsOfficialNewsService news;
  public BbsNewsController(BbsOfficialNewsService news) { this.news = news; }

  @GetMapping("/{appId}/news")
  public BbsNewsResponse news(@PathVariable long appId) { return news.get(appId); }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> invalidId() {
    return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_GAME_SEARCH", "게임 번호를 확인해 주세요."));
  }
}
