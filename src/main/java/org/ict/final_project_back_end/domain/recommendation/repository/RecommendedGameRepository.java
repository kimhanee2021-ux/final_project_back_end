package org.ict.final_project_back_end.domain.recommendation.repository;

import org.ict.final_project_back_end.domain.recommendation.entity.RecommendedGame;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecommendedGameRepository extends JpaRepository<RecommendedGame, Long> {

  List<RecommendedGame> findAllByUserIdOrderByRankAsc(Long userId);

  // 호출하는 쪽에서 트랜잭션이 필요하다
  void deleteByUserId(Long userId);
}
