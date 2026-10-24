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
  interface RankedCandidate extends Candidate { Integer getMatchRank(); }
  @Query("""
      select g.appId as appId, g.name as name,
        min(case when n.normalizedName = :original and n.kind = :canonical then 0
                 when n.normalizedName = :original then 1 else 2 end) as matchRank
      from GameSearchName n join n.game g
      where n.active = true and n.compactName = :key
      group by g.appId, g.name order by matchRank, g.name, g.appId
      """)
  @QueryHints(@QueryHint(name = "jakarta.persistence.query.timeout", value = "2000"))
  List<RankedCandidate> indexedExact(@Param("key") String key, @Param("original") String original,
      @Param("canonical") GameSearchName.Kind canonical, Pageable limit);
  interface NameRow { Long getAppId(); String getName(); String getSearchName(); }
  @Query("""
      select g.appId as appId, g.name as name, n.normalizedName as searchName
      from GameSearchName n join n.game g where n.active = true
        and length(n.normalizedName) between :minimum and :maximum
        and (n.normalizedName like :prefix escape '!' or n.normalizedName like :suffix escape '!')
      order by n.normalizedName, g.appId
      """)
  @QueryHints(@QueryHint(name = "jakarta.persistence.query.timeout", value = "2000"))
  List<NameRow> nearby(@Param("minimum") int minimum, @Param("maximum") int maximum,
      @Param("prefix") String prefix, @Param("suffix") String suffix, Pageable limit);
  @Query("""
      select distinct g.appId as appId, g.name as name from GameSearchName n join n.game g
      where n.active = true
        and n.compactName = :key
      order by g.name, g.appId
      """)
  @QueryHints(@QueryHint(name = "jakarta.persistence.query.timeout", value = "2000"))
  List<Candidate> compact(@Param("key") String key, Pageable limit);
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
