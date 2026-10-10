package org.ict.final_project_back_end.domain.board.controller;
import java.net.URI;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import org.ict.final_project_back_end.domain.board.dto.*;
import org.ict.final_project_back_end.domain.board.service.BoardCommentService;
import org.ict.final_project_back_end.global.swagger.BoardCommentApiDocs;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/board/{postId}/comments")
public class BoardCommentController implements BoardCommentApiDocs {
  private final BoardCommentService service;
  public BoardCommentController(BoardCommentService service) { this.service = service; }
  @Override
  @PostMapping
  public ResponseEntity<BoardCommentResponse> create(@AuthenticationPrincipal Long userId,
      @PathVariable("postId") Long postId, @Valid @RequestBody BoardCommentCreateRequest request) {
    var response = service.create(userId, postId, request);
    return ResponseEntity.created(URI.create("/api/board/" + postId + "/comments/" + response.id())).body(response);
  }
  @Override
  @GetMapping
  public ResponseEntity<BoardCommentPageResponse> list(@PathVariable("postId") Long postId,
      @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
      @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
    return ResponseEntity.ok(service.list(postId, page, size));
  }
  @Override
  @PutMapping("/{commentId}")
  public ResponseEntity<BoardCommentResponse> update(@AuthenticationPrincipal Long userId,
      @PathVariable("postId") Long postId, @PathVariable("commentId") Long commentId,
      @Valid @RequestBody BoardCommentRequest request) {
    return ResponseEntity.ok(service.update(userId, postId, commentId, request));
  }
  @Override
  @DeleteMapping("/{commentId}")
  public ResponseEntity<Void> delete(@AuthenticationPrincipal Long userId,
      @PathVariable("postId") Long postId, @PathVariable("commentId") Long commentId) {
    service.delete(userId, postId, commentId);
    return ResponseEntity.noContent().build();
  }
}
