package org.ict.final_project_back_end.domain.board.controller;
import org.ict.final_project_back_end.domain.board.dto.BoardLikeResponse;
import org.ict.final_project_back_end.domain.board.service.BoardLikeService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/board/{postId}/likes")
public class BoardLikeController {
  private final BoardLikeService service;
  public BoardLikeController(BoardLikeService service) { this.service = service; }
  @GetMapping
  public BoardLikeResponse status(@AuthenticationPrincipal Long userId, @PathVariable("postId") Long postId) {
    return service.status(userId, postId);
  }
  @PutMapping
  public BoardLikeResponse like(@AuthenticationPrincipal Long userId, @PathVariable("postId") Long postId) {
    return service.like(userId, postId);
  }
  @DeleteMapping
  public BoardLikeResponse cancel(@AuthenticationPrincipal Long userId, @PathVariable("postId") Long postId) {
    return service.cancel(userId, postId);
  }
}
