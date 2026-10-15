package org.ict.final_project_back_end.domain.game.repository;

import org.ict.final_project_back_end.domain.game.entity.FeaturedCategoryGame;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeaturedCategoryGameRepository extends JpaRepository<FeaturedCategoryGame, Long> {

  List<FeaturedCategoryGame> findAllByOrderByCategoryOrderAscRankOrderAsc();
}
