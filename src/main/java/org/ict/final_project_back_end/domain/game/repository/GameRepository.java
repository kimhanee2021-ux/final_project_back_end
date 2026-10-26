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

  // 구매내역의 상품 이름으로 게임을 찾을 때 사용 (대소문자 무시, 이름이 정확히 같은 게임).
  // 이름만 같은 다른 게임이 여럿일 수 있어 스팀 추천 수가 가장 많은(가장 알려진) 게임을 고른다.
  Optional<Game> findFirstByNameIgnoreCaseOrderByRecommendationsDesc(String name);

  // AI 게임 검색: GPT가 말한 게임 이름의 앞부분으로 시작하는 후보 게임 (추천 수가 많은 순, 최대 100개).
  // pattern은 escape한 "앞부분%". name 인덱스(docs/db/games-name-index.sql)로 바로 찾고(없으면 13만 행을 읽어 약 3초),
  // ™®© 기호를 무시한 정확한 비교는 서비스가 후보 안에서 한다. 컬럼 콜레이션(ai_ci)이라 대소문자는 구분하지 않는다.
  @Query(value = """
      SELECT * FROM games WHERE name LIKE :pattern ESCAPE '!'
      ORDER BY recommendations IS NULL, recommendations DESC, app_id LIMIT 100
      """, nativeQuery = true)
  List<Game> findNameCandidates(@Param("pattern") String pattern);

  // appId 오름차순으로 after 다음 것부터 한 페이지만큼 appId만 가져온다 (가격 동기화가 전체 게임을 훑을 때 사용)
  @Query("select g.appId from Game g where g.appId > :after order by g.appId")
  List<Long> findAppIdsAfter(@Param("after") long after, Pageable pageable);

  interface BbsGameRow {
    Long getAppId();
    String getName();
    String getGenre();
    String getThumbnailUrl();
  }

  // BBS 게임 검색: 게임 번호가 같으면 먼저, 이름이 정확히 같으면 다음, 이름에 검색어가 들어가면 그다음 순서로 찾는다.
  // 안쪽 쿼리가 name 인덱스(docs/db/games-name-index.sql)만 훑어 번호를 고르고, 고른 행에만 나머지 칸을 붙인다.
  // 그냥 games를 훑으면 큰 TEXT 칸까지 읽어 약 3초가 걸려 쿼리 제한 시간(3초)에 걸린다 (인덱스로 0.03초, 2026-10 측정).
  // 컬럼 콜레이션(ai_ci)이라 대소문자는 구분하지 않는다.
  @Query(value = """
      SELECT g.app_id AS appId, g.name AS name, g.genre AS genre, g.thumbnail_url AS thumbnailUrl
      FROM games g JOIN (
        SELECT app_id, name AS sort_name,
               CASE WHEN app_id = :appId THEN 0 WHEN name = :query THEN 1 ELSE 3 END AS rank_order
        FROM games WHERE app_id = :appId OR name LIKE :pattern ESCAPE '!'
        ORDER BY rank_order, sort_name, app_id LIMIT :limit
      ) hit ON hit.app_id = g.app_id
      ORDER BY hit.rank_order, hit.sort_name, g.app_id
      """, nativeQuery = true)
  List<BbsGameRow> searchForBbsByName(@Param("query") String query, @Param("pattern") String pattern,
      @Param("appId") Long appId, @Param("limit") int limit);

  // 말한 게임 이름을 하나로 정할 때: 게임 번호나 이름이 정확히 같은 게임 (name 인덱스로 바로 찾는다)
  @Query(value = """
      SELECT app_id AS appId, name AS name, genre AS genre, thumbnail_url AS thumbnailUrl
      FROM games WHERE app_id = :appId OR name = :query ORDER BY app_id LIMIT 2
      """, nativeQuery = true)
  List<BbsGameRow> findExactForBbsByName(@Param("query") String query, @Param("appId") Long appId);

  @Query("select g.appId as appId, g.name as name, g.genre as genre, g.thumbnailUrl as thumbnailUrl from Game g where g.appId = :appId")
  Optional<BbsGameRow> findForBbs(@Param("appId") long appId);

}
