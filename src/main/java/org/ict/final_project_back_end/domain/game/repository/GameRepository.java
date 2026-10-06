package org.ict.final_project_back_end.domain.game.repository;

import org.ict.final_project_back_end.domain.game.entity.Game;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GameRepository extends JpaRepository<Game, Long> {

  Optional<Game> findByAppId(Long appId);

  List<Game> findAllByAppIdIn(Collection<Long> appIds);

  Page<Game> findByNameContainingIgnoreCase(String keyword, Pageable pageable);

  // appId 커서 방식 조회(오름차순). 처음 페이지는 findByOrderByAppIdAsc, 이후 페이지는 afterAppId보다 큰 appId를 가져온다
  List<Game> findByOrderByAppIdAsc(Pageable pageable);

  List<Game> findByAppIdGreaterThanOrderByAppIdAsc(Long appId, Pageable pageable);
}
