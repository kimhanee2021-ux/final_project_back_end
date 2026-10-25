package org.ict.final_project_back_end.domain.recommend.controller;

import org.ict.final_project_back_end.domain.recommend.controller.docs.RecommendationApiDocs;
import org.ict.final_project_back_end.domain.recommend.dto.RecommendationResponse;
import org.ict.final_project_back_end.domain.recommend.service.RecommendationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// AI 게임 추천 API. 프론트는 FastAPI 추천 서버를 직접 부르지 않고 이 API만 부른다.
@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController implements RecommendationApiDocs {

  private final RecommendationService recommendationService;

  public RecommendationController(RecommendationService recommendationService) {
    this.recommendationService = recommendationService;
  }

  // 로그인한 사용자 본인의 추천만 조회한다 (userId는 JwtAuthFilter가 넣은 값)
  @Override
  @GetMapping("/me")
  public ResponseEntity<RecommendationResponse> getMyRecommendations(
      @AuthenticationPrincipal Long userId,
      @RequestParam(name = "limit", defaultValue = "" + RecommendationService.DEFAULT_LIMIT) int limit,
      @RequestParam(name = "newSlots", defaultValue = "" + RecommendationService.DEFAULT_NEW_SLOTS) int newSlots) {
    return ResponseEntity.ok(recommendationService.recommendForUser(userId, limit, newSlots));
  }
}
