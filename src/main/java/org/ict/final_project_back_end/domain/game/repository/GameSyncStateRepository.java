package org.ict.final_project_back_end.domain.game.repository;

import org.ict.final_project_back_end.domain.game.entity.GameSyncState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameSyncStateRepository extends JpaRepository<GameSyncState, Long> {
}
