package org.ict.final_project_back_end.domain.game.service;

import org.ict.final_project_back_end.domain.game.entity.Game;
import org.ict.final_project_back_end.domain.game.exception.GameNotFoundException;
import org.ict.final_project_back_end.domain.game.repository.GameRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GameIdentityValidator {
  private final GameRepository games;
  public GameIdentityValidator(GameRepository games) { this.games = games; }
  public Game require(String input) {
    if (input == null || !input.matches("[0-9]{1,10}"))
      throw GameSearchException.invalid("유효한 Steam 게임 번호가 필요합니다.");
    long appId = Long.parseLong(input);
    if (appId <= 0 || appId > 0xffffffffL) throw GameSearchException.invalid("게임 번호가 허용 범위를 벗어났습니다.");
    // 현재 Game에는 숨김/노출 필드가 없다. 정책이 생기면 이 경로에 한 번만 추가한다.
    return games.findById(appId).orElseThrow(GameNotFoundException::new);
  }
}
