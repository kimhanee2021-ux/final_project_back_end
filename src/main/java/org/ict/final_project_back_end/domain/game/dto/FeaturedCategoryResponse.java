package org.ict.final_project_back_end.domain.game.dto;

import java.util.List;

public record FeaturedCategoryResponse(String categoryKey, String categoryName,
    List<FeaturedGameItemResponse> games) {
}
