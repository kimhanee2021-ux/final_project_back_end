package org.ict.final_project_back_end.domain.notice.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.net.URI;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.ict.final_project_back_end.domain.notice.dto.*;
import org.ict.final_project_back_end.domain.notice.service.NoticeService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "공지사항", description = "공지사항 목록·상세·작성·수정·삭제 API. 현재 회원 작성, 작성자 수정·삭제 정책을 사용합니다.")
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/notices")
public class NoticeController {
  private final NoticeService service;

  public NoticeController(NoticeService service) { this.service = service; }

  @PostMapping
  @Operation(summary = "공지사항 — 공지 작성")
  public ResponseEntity<NoticeDetailResponse> create(@AuthenticationPrincipal Long userId,
      @Valid @RequestBody NoticeRequest request) {
    var response = service.create(userId, request);
    return ResponseEntity.created(URI.create("/api/notices/" + response.id())).body(response);
  }

  @GetMapping
  @Operation(summary = "공지사항 — 목록 조회")
  public NoticePageResponse list(@RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
      @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
    return service.list(page, size);
  }

  @GetMapping("/{id}")
  @Operation(summary = "공지사항 — 상세 조회")
  public NoticeDetailResponse find(@PathVariable("id") Long id) { return service.find(id); }

  @PutMapping("/{id}")
  @Operation(summary = "공지사항 — 공지 수정")
  public NoticeDetailResponse update(@AuthenticationPrincipal Long userId, @PathVariable("id") Long id,
      @Valid @RequestBody NoticeRequest request) { return service.update(userId, id, request); }

  @DeleteMapping("/{id}")
  @Operation(summary = "공지사항 — 공지 삭제")
  public ResponseEntity<Void> delete(@AuthenticationPrincipal Long userId, @PathVariable("id") Long id) {
    service.delete(userId, id);
    return ResponseEntity.noContent().build();
  }
}
