package org.ict.final_project_back_end.domain.game.service;

import org.ict.final_project_back_end.domain.game.dto.FeaturedCategoryResponse;
import org.ict.final_project_back_end.domain.game.dto.FeaturedGameItemResponse;

import java.util.List;

public interface FeaturedGameService {
  List<FeaturedGameItemResponse> getFeatured();
  List<FeaturedCategoryResponse> getFeaturedCategories();
}
