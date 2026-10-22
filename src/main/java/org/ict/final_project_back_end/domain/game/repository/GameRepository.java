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
}
