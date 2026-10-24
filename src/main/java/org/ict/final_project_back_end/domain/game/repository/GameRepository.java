package org.ict.final_project_back_end.domain.game.repository;

import org.ict.final_project_back_end.domain.game.entity.Game;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GameRepository extends JpaRepository<Game, Long>, JpaSpecificationExecutor<Game> {

  // 구매내역의 상품 이름으로 게임을 찾을 때 사용 (대소문자 무시, 이름이 정확히 같은 첫 번째 게임)
  Optional<Game> findFirstByNameIgnoreCase(String name);

  // appId 오름차순으로 after 다음 것부터 한 페이지만큼 appId만 가져온다 (가격 동기화가 전체 게임을 훑을 때 사용)
  @Query("select g.appId from Game g where g.appId > :after order by g.appId")
  List<Long> findAppIdsAfter(@Param("after") long after, Pageable pageable);

  interface BbsGameRow {
    Long getAppId();
    String getName();
    String getGenre();
    String getThumbnailUrl();
  }

  // EXISTS prevents duplicate results when a game has several matching aliases.
  // Scalar projection avoids loading TEXT columns or counting the entire catalog.
  @Query("""
      select g.appId as appId, g.name as name, g.genre as genre, g.thumbnailUrl as thumbnailUrl
      from Game g
      where g.appId = :appId or lower(g.name) like :pattern escape '!'
         or exists (select n.id from GameSearchName n where n.game = g and n.active = true
                    and n.normalizedName like :pattern escape '!')
      order by case when g.appId = :appId then 0
                    when lower(g.name) = :query then 1
                    when exists (select a.id from GameSearchName a where a.game = g and a.active = true
                                 and a.normalizedName = :query) then 2 else 3 end,
               g.name, g.appId
      """)
  @org.springframework.data.jpa.repository.QueryHints(
      @jakarta.persistence.QueryHint(name = "jakarta.persistence.query.timeout", value = "3000"))
  List<BbsGameRow> searchForBbs(@Param("query") String query, @Param("pattern") String pattern,
      @Param("appId") Long appId, Pageable pageable);

  @Query("select g.appId as appId, g.name as name, g.genre as genre, g.thumbnailUrl as thumbnailUrl from Game g where g.appId = :appId")
  Optional<BbsGameRow> findForBbs(@Param("appId") long appId);
}
