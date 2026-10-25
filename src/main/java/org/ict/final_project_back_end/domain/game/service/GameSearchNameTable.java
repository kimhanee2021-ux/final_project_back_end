package org.ict.final_project_back_end.domain.game.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// 게임 별명 테이블(game_search_names: "카스" -> Counter-Strike 같은 한국어 별명)이 이 DB에 있는지.
// BBS 팀 DB에는 있지만 서버 시작 시 테이블을 만들지 않아서(ddl-auto=none) 없는 DB도 있다.
// 없는 DB에서 별명까지 찾는 쿼리를 쓰면 모든 검색이 500이 나므로, 게임 이름만으로 찾게 한다.
// 처음 쓸 때 한 번만 확인하고 기억한다 (테이블을 새로 만들었으면 서버를 다시 시작한다).
@Slf4j
@Component
public class GameSearchNameTable {

  private final JdbcTemplate jdbc;
  private volatile Boolean available;

  public GameSearchNameTable(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public boolean available() {
    Boolean known = available;
    if (known == null) {
      known = check();
      available = known;
    }
    return known;
  }

  private boolean check() {
    try {
      jdbc.queryForObject("SELECT COUNT(*) FROM game_search_names WHERE 1 = 0", Integer.class);
      return true;
    } catch (DataAccessException e) {
      log.warn("game_search_names 테이블이 없어 BBS 게임 검색은 게임 이름만으로 찾습니다");
      return false;
    }
  }
}
