package org.ict.final_project_back_end.domain.briefing.repository;
import java.util.*;
import org.ict.final_project_back_end.domain.briefing.entity.OfficialNews;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
public interface OfficialNewsRepository extends JpaRepository<OfficialNews,Long> {
  long countByKoreanSummaryIsNotNull();
  // 같은 게임이 글마다 다른 이름으로 저장될 수 있어 appId 하나당 한 줄로 묶는다.
  @Query("select n.appId as appId,max(n.gameName) as gameName from OfficialNews n group by n.appId order by max(n.gameName)")
  List<GameOption> games();
  interface GameOption { Long getAppId(); String getGameName(); }
  Optional<OfficialNews> findByAppIdAndGid(Long appId,String gid);
  List<OfficialNews> findTop5ByAppIdOrderByPublishedAtDesc(Long appId);
  @Query("select n from OfficialNews n where (:appId is null or n.appId=:appId) order by n.publishedAt desc,n.id desc")
  Page<OfficialNews> list(Long appId,Pageable page);
  @Query("select n.appId from OfficialNews n left join GameBriefing b on n.appId=b.appId group by n.appId,b.checkedAt having b.checkedAt is null or max(n.fetchedAt)>b.checkedAt order by min(n.fetchedAt),n.appId")
  List<Long> pending(Pageable page);
}
