package org.ict.final_project_back_end.domain.notice.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.net.URI;
import org.ict.final_project_back_end.domain.notice.dto.*;
import org.ict.final_project_back_end.domain.notice.service.NoticeService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notices")
public class NoticeController {
  private final NoticeService service;

  public NoticeController(NoticeService service) { this.service = service; }

  @PostMapping
  public ResponseEntity<NoticeDetailResponse> create(@AuthenticationPrincipal Long userId,
      @Valid @RequestBody NoticeRequest request) {
    var response = service.create(userId, request);
    return ResponseEntity.created(URI.create("/api/notices/" + response.id())).body(response);
  }

  @GetMapping
  public NoticePageResponse list(@RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
      @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
    return service.list(page, size);
  }

  @GetMapping("/{id}")
  public NoticeDetailResponse find(@PathVariable("id") Long id) { return service.find(id); }

  @PutMapping("/{id}")
  public NoticeDetailResponse update(@AuthenticationPrincipal Long userId, @PathVariable("id") Long id,
      @Valid @RequestBody NoticeRequest request) { return service.update(userId, id, request); }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@AuthenticationPrincipal Long userId, @PathVariable("id") Long id) {
    service.delete(userId, id);
    return ResponseEntity.noContent().build();
  }
}
