package org.ict.final_project_back_end.domain.search.controller;

import org.ict.final_project_back_end.domain.search.controller.docs.SearchApiDocs;
import org.ict.final_project_back_end.domain.search.dto.NaturalSearchResponse;
import org.ict.final_project_back_end.domain.search.service.SearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 자연어 게임 검색 API. 실제 로직은 SearchService에 위임하고 HTTP 매핑만 담당한다.
@Validated
@RestController
@RequestMapping("/api/search")
public class SearchController implements SearchApiDocs {

  private static final int DEFAULT_LIMIT = 10;

  private final SearchService searchService;

  public SearchController(SearchService searchService) {
    this.searchService = searchService;
  }

  // AI 호출 비용 때문에 로그인한 사용자만 쓸 수 있고, 사용자별로 분당 횟수를 제한한다.
  @Override
  @GetMapping("/natural")
  public ResponseEntity<NaturalSearchResponse> searchNatural(
      @AuthenticationPrincipal Long userId,
      @RequestParam String query,
      @RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit) {
    return ResponseEntity.ok(searchService.search(userId, query, limit));
  }
}
