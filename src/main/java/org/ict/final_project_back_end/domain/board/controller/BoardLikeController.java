package org.ict.final_project_back_end.domain.board.controller;
import org.ict.final_project_back_end.domain.board.dto.BoardLikeResponse;
import org.ict.final_project_back_end.domain.board.service.BoardLikeService;
import org.ict.final_project_back_end.global.swagger.BoardLikeApiDocs;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/board/{postId}/likes")
public class BoardLikeController implements BoardLikeApiDocs {
  private final BoardLikeService service;
  public BoardLikeController(BoardLikeService service) { this.service = service; }
  @Override
  @GetMapping
  public ResponseEntity<BoardLikeResponse> status(@AuthenticationPrincipal Long userId, @PathVariable("postId") Long postId) {
    return ResponseEntity.ok(service.status(userId, postId));
  }
  @Override
  @PutMapping
  public ResponseEntity<BoardLikeResponse> like(@AuthenticationPrincipal Long userId, @PathVariable("postId") Long postId) {
    return ResponseEntity.ok(service.like(userId, postId));
  }
  @Override
  @DeleteMapping
  public ResponseEntity<BoardLikeResponse> cancel(@AuthenticationPrincipal Long userId, @PathVariable("postId") Long postId) {
    return ResponseEntity.ok(service.cancel(userId, postId));
  }
}
