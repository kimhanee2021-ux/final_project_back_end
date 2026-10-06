package org.ict.final_project_back_end.domain.game.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 전체 게임 목록 동기화의 진행 상태(행은 항상 1개). 중간에 서버가 꺼져도 lastAppId부터 이어서 가져오기 위해 DB에 둔다.
@Entity
@Table(name = "game_sync_states")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameSyncState {

  public static final long SINGLETON_ID = 1L;

  // 고정 값(1)을 직접 넣는다
  @Id
  private Long id;

  // 마지막으로 저장한 appId. 다음 요청은 이 값 다음부터 시작한다 (0이면 처음부터)
  @Column(name = "last_app_id", nullable = false)
  private Long lastAppId;

  // 한 바퀴(전체 목록)를 끝까지 가져왔는지
  @Column(nullable = false)
  private boolean completed;

  @Column(name = "last_completed_at")
  private LocalDateTime lastCompletedAt;

  public static GameSyncState create() {
    GameSyncState state = new GameSyncState();
    state.id = SINGLETON_ID;
    state.lastAppId = 0L;
    state.completed = false;
    return state;
  }

  public void advance(long appId) {
    this.lastAppId = appId;
  }

  public void complete(LocalDateTime now) {
    this.completed = true;
    this.lastCompletedAt = now;
  }

  // 완료된 뒤 새 한 바퀴를 시작할 때 커서를 처음으로 되돌린다
  public void restart() {
    this.lastAppId = 0L;
    this.completed = false;
  }
}
