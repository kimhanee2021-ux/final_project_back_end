package org.ict.final_project_back_end.domain.board.controller;

import jakarta.validation.Valid;
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
}
