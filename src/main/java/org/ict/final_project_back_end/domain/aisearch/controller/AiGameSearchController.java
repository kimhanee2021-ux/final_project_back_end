package org.ict.final_project_back_end.domain.aisearch.controller;

import org.ict.final_project_back_end.domain.aisearch.controller.docs.AiGameSearchApiDocs;
import org.ict.final_project_back_end.domain.aisearch.dto.AiGameSearchResponse;
import org.ict.final_project_back_end.domain.aisearch.service.AiGameSearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// AI 게임 검색 API. GPT 호출 비용이 들어 로그인한 사용자만 쓴다(/api/** 는 SecurityConfig에서 인증 필요).
@RestController
@RequestMapping("/api/ai-search")
public class AiGameSearchController implements AiGameSearchApiDocs {

  private final AiGameSearchService searchService;

  public AiGameSearchController(AiGameSearchService searchService) {
    this.searchService = searchService;
  }

  @Override
  @GetMapping
  public ResponseEntity<AiGameSearchResponse> search(
      @RequestParam(name = "q") String query,
      @RequestParam(name = "limit", defaultValue = "" + AiGameSearchService.DEFAULT_LIMIT) int limit) {
    return ResponseEntity.ok(searchService.search(query, limit));
  }
}
