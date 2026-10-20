package org.ict.final_project_back_end.domain.game.controller;

import org.ict.final_project_back_end.domain.game.dto.FeaturedCategoryResponse;
import org.ict.final_project_back_end.domain.game.dto.FeaturedGameItemResponse;
import org.ict.final_project_back_end.domain.game.service.FeaturedGameService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/games")
public class FeaturedGameController {

  private final FeaturedGameService service;

  public FeaturedGameController(FeaturedGameService service) {
    this.service = service;
  }

  @GetMapping("/featured")
  public ResponseEntity<List<FeaturedGameItemResponse>> featured() {
    return ResponseEntity.ok(service.getFeatured());
  }

  @GetMapping("/featured-categories")
  public ResponseEntity<List<FeaturedCategoryResponse>> featuredCategories() {
    return ResponseEntity.ok(service.getFeaturedCategories());
  }
}
