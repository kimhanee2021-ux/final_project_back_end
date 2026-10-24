package org.ict.final_project_back_end.domain.recommendation.controller;

import org.ict.final_project_back_end.domain.recommendation.controller.docs.RecommendationApiDocs;
import org.ict.final_project_back_end.domain.recommendation.dto.RecommendationResponse;
import org.ict.final_project_back_end.domain.recommendation.service.RecommendationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 게임 추천 API. 실제 로직은 RecommendationService에 위임하고 HTTP 매핑만 담당한다.
@Validated
@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController implements RecommendationApiDocs {

  private static final int DEFAULT_LIMIT = 10;

  private final RecommendationService recommendationService;

  public RecommendationController(RecommendationService recommendationService) {
    this.recommendationService = recommendationService;
  }

  // 추천 대상은 토큰의 사용자 본인뿐이다. userId를 파라미터로 받지 않는다.
  @Override
  @GetMapping
  public ResponseEntity<RecommendationResponse> getRecommendations(
      @AuthenticationPrincipal Long userId,
      @RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit) {
    return ResponseEntity.ok(recommendationService.getRecommendations(userId, limit));
  }
}
