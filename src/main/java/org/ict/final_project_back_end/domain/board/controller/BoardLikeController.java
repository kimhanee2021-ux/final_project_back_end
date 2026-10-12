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
  // DELETE가 204가 아니라 200+바디를 반환한다: 프론트(useBoardPost.js)가 이 바디의 likeCount/liked로
  // 상태를 직접 갱신하므로, 204로 "표준화"하면 프론트가 깨진다.
  @Override
  @DeleteMapping
  public ResponseEntity<BoardLikeResponse> cancel(@AuthenticationPrincipal Long userId, @PathVariable("postId") Long postId) {
    return ResponseEntity.ok(service.cancel(userId, postId));
  }
}
