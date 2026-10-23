package org.ict.final_project_back_end.domain.gamenews.entity;

import jakarta.persistence.*;
import java.time.Instant;
import org.ict.final_project_back_end.domain.game.entity.Game;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity @Table(name = "game_news_support")
public class GameNewsSupport {
  public enum Status { SUPPORTED, UNSUPPORTED, UNKNOWN }
  @Id @Column(name = "app_id") private Long appId;
  @MapsId @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "app_id") @OnDelete(action = OnDeleteAction.CASCADE) private Game game;
  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Status status;
  @Column(nullable = false, length = 100) private String source;
  @Column(name = "last_checked_at") private Instant lastCheckedAt;
  protected GameNewsSupport() {}
  public GameNewsSupport(Game game, Status status, String source) {
    this.appId = game.getAppId(); this.game = game; this.status = status; this.source = source; this.lastCheckedAt = Instant.now();
  }
  public Status status() { return status; }
}
