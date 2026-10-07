package org.ict.final_project_back_end.domain.board.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;
import org.ict.final_project_back_end.domain.board.dto.BoardPostPageResponse;
import org.springframework.web.bind.annotation.RequestParam;
import java.net.URI;
import org.ict.final_project_back_end.domain.board.dto.BoardPostCreateRequest;
import org.ict.final_project_back_end.domain.board.dto.BoardPostCreateResponse;
import org.ict.final_project_back_end.domain.board.dto.BoardPostDetailResponse;
import org.ict.final_project_back_end.domain.board.service.BoardPostService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/board")
public class BoardController {
  private final BoardPostService service;

  public BoardController(BoardPostService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<BoardPostCreateResponse> create(@AuthenticationPrincipal Long userId,
      @Valid @RequestBody BoardPostCreateRequest request) {
    BoardPostCreateResponse response = service.createPost(userId, request);
    return ResponseEntity.created(URI.create("/api/board/" + response.id())).body(response);
  }

  @GetMapping("/{id}")
  public BoardPostDetailResponse get(@PathVariable("id") Long id) {
    return service.findPost(id);
  }

  @GetMapping
  public BoardPostPageResponse list(
      @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
      @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size,
      @RequestParam(name = "category", required = false) @Size(max = 30) String category,
      @RequestParam(name = "keyword", required = false) @Size(max = 200) String keyword) {
    return service.listPosts(page, size, category, keyword);
  }

  @org.springframework.web.bind.annotation.PutMapping("/{id}")
  public BoardPostDetailResponse update(@AuthenticationPrincipal Long userId,
      @PathVariable("id") Long id,
      @Valid @RequestBody org.ict.final_project_back_end.domain.board.dto.BoardPostUpdateRequest request) {
    return service.updatePost(userId, id, request);
  }

  @org.springframework.web.bind.annotation.DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@AuthenticationPrincipal Long userId, @PathVariable("id") Long id) {
    service.deletePost(userId, id);
    return ResponseEntity.noContent().build();
  }
}
