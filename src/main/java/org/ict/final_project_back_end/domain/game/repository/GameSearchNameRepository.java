package org.ict.final_project_back_end.domain.game.repository;

import java.util.List;
import org.ict.final_project_back_end.domain.game.entity.GameSearchName;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import jakarta.persistence.QueryHint;
import org.springframework.data.repository.query.Param;

public interface GameSearchNameRepository extends JpaRepository<GameSearchName, Long> {
  interface Candidate { Long getAppId(); String getName(); }
  @Query("select n from GameSearchName n join fetch n.game g where g.appId in :ids")
  List<GameSearchName> forGames(@Param("ids") List<Long> ids);
  @Query("""
      select distinct g.appId as appId, g.name as name from GameSearchName n join n.game g
      where n.active = true and n.kind = :kind and n.normalizedName = :name
      order by g.name, g.appId
      """)
  @QueryHints(@QueryHint(name = "jakarta.persistence.query.timeout", value = "2000"))
  List<Candidate> exact(@Param("name") String name, @Param("kind") GameSearchName.Kind kind, Pageable limit);
  @Query("""
      select distinct g.appId as appId, g.name as name from GameSearchName n join n.game g
      where n.active = true and n.normalizedName like :pattern escape '!'
      order by g.name, g.appId
      """)
  @QueryHints(@QueryHint(name = "jakarta.persistence.query.timeout", value = "2000"))
  List<Candidate> matching(@Param("pattern") String pattern, Pageable limit);
}
