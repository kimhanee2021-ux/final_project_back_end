package org.ict.final_project_back_end.domain.game.repository;

import org.ict.final_project_back_end.domain.game.entity.GamePriceKrw;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface GamePriceKrwRepository extends JpaRepository<GamePriceKrw, Long> {

  // 아직 원화 가격을 받지 않은 게임 appId (가격 시드가 중단돼도 이어서 진행할 수 있게)
  @Query("SELECT g.appId FROM Game g WHERE NOT EXISTS (SELECT 1 FROM GamePriceKrw p WHERE p.appId = g.appId) ORDER BY g.appId")
  List<Long> findAppIdsWithoutPrice();
}
