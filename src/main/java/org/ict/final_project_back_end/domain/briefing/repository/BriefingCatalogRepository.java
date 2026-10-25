package org.ict.final_project_back_end.domain.briefing.repository;
import java.util.List;
import org.ict.final_project_back_end.domain.briefing.entity.OfficialNews;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.Repository;
import org.springframework.data.domain.Pageable;
public interface BriefingCatalogRepository extends Repository<OfficialNews,Long> {
  @Query("select n.appId as appId,max(n.gameName) as gameName from OfficialNews n where n.appId>:after group by n.appId order by n.appId")
  List<OfficialNewsRepository.GameOption> next(long after,Pageable page);
  @Query("select count(distinct n.appId) from OfficialNews n")
  long count();
}
