package org.ict.final_project_back_end.domain.briefing.repository;
import org.ict.final_project_back_end.domain.briefing.entity.GameBriefing;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
public interface GameBriefingRepository extends JpaRepository<GameBriefing,Long> {
  @Query("select b from GameBriefing b where (:appId is null or b.appId=:appId) order by b.analyzedAt desc,b.appId")
  Page<GameBriefing> list(Long appId,Pageable page);
}
