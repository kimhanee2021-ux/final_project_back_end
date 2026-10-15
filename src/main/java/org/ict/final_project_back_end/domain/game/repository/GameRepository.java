package org.ict.final_project_back_end.domain.game.repository;

import org.ict.final_project_back_end.domain.game.entity.Game;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface GameRepository extends JpaRepository<Game, Long>, JpaSpecificationExecutor<Game> {

  // 구매내역의 상품 이름으로 게임을 찾을 때 사용 (대소문자 무시, 이름이 정확히 같은 첫 번째 게임)
  Optional<Game> findFirstByNameIgnoreCase(String name);
}
