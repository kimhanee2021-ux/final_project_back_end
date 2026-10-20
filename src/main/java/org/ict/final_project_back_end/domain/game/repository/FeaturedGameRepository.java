package org.ict.final_project_back_end.domain.game.repository;

import org.ict.final_project_back_end.domain.game.entity.FeaturedGame;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeaturedGameRepository extends JpaRepository<FeaturedGame, Long> {

  List<FeaturedGame> findAllByOrderByRankOrderAsc();
}
