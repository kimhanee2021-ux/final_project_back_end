package org.ict.final_project_back_end.domain.gamenews.repository;

import org.ict.final_project_back_end.domain.gamenews.entity.GameNewsSyncState;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface GameNewsSyncStateRepository extends JpaRepository<GameNewsSyncState, Long> {
  List<GameNewsSyncState> findByNextCollectAtLessThanEqualOrderByNextCollectAtAscAppIdAsc(
      LocalDateTime now, Pageable pageable);
}
