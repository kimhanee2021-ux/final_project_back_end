package org.ict.final_project_back_end.domain.board.controller;

import org.ict.final_project_back_end.domain.board.dto.BoardPostPageResponse;
import org.springframework.web.bind.annotation.RequestParam;
import java.net.URI;
import org.ict.final_project_back_end.domain.board.dto.BoardPostCreateRequest;
import org.ict.final_project_back_end.domain.board.dto.BoardPostCreateResponse;
import org.ict.final_project_back_end.domain.board.dto.BoardPostDetailResponse;
import org.ict.final_project_back_end.domain.board.service.BoardPostService;
import org.ict.final_project_back_end.global.swagger.BoardApiDocs;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/board")
@Validated
public class BoardController implements BoardApiDocs {
  private final BoardPostService service;

  public BoardController(BoardPostService service) {
    this.service = service;
  }

  @Override
  @PostMapping
  public ResponseEntity<BoardPostCreateResponse> create(@AuthenticationPrincipal Long userId,
      @RequestBody BoardPostCreateRequest request) {
    BoardPostCreateResponse response = service.createPost(userId, request);
    return ResponseEntity.created(URI.create("/api/board/" + response.id())).body(response);
  }

  @Override
  @GetMapping("/{id}")
  public ResponseEntity<BoardPostDetailResponse> get(@PathVariable("id") Long id) {
    return ResponseEntity.ok(service.findPost(id));
  }

  @Override
  @GetMapping
  public ResponseEntity<BoardPostPageResponse> list(
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size,
      @RequestParam(name = "category", required = false) String category,
      @RequestParam(name = "keyword", required = false) String keyword) {
    return ResponseEntity.ok(service.listPosts(page, size, category, keyword));
  }

  @Override
  @org.springframework.web.bind.annotation.PutMapping("/{id}")
  public ResponseEntity<BoardPostDetailResponse> update(@AuthenticationPrincipal Long userId,
      @PathVariable("id") Long id,
      @RequestBody org.ict.final_project_back_end.domain.board.dto.BoardPostUpdateRequest request) {
    return ResponseEntity.ok(service.updatePost(userId, id, request));
  }

  @Override
  @org.springframework.web.bind.annotation.DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@AuthenticationPrincipal Long userId, @PathVariable("id") Long id) {
    service.deletePost(userId, id);
    return ResponseEntity.noContent().build();
  }
}
