package org.ict.final_project_back_end.domain.game.repository;

import org.ict.final_project_back_end.domain.game.entity.GameDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GameDetailRepository extends JpaRepository<GameDetail, Long> {

  Optional<GameDetail> findByAppId(Long appId);
}
