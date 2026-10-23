package org.ict.final_project_back_end.domain.gamenews.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.ict.final_project_back_end.domain.game.entity.Game;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** 게임별 수집 상태. 모든 시각은 UTC이며, 실제 스케줄 실행은 이 엔티티가 담당하지 않는다. */
@Entity
@Table(name = "game_news_sync_state",
    indexes = @Index(name = "idx_game_news_sync_due", columnList = "next_collect_at,app_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class GameNewsSyncState {
  @Id
  @Column(name = "app_id")
  private Long appId;

  @MapsId
  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "app_id", nullable = false, updatable = false,
      foreignKey = @ForeignKey(name = "fk_game_news_sync_game"))
  private Game game;

  @Column(name = "last_attempt_at", columnDefinition = "DATETIME(6)")
  private LocalDateTime lastAttemptAt;

  @Column(name = "last_success_at", columnDefinition = "DATETIME(6)")
  private LocalDateTime lastSuccessAt;

  @Column(name = "next_collect_at", nullable = false, columnDefinition = "DATETIME(6)")
  private LocalDateTime nextCollectAt;

  @Builder.Default
  @Column(name = "consecutive_failures", nullable = false)
  private int consecutiveFailures = 0;

  @Column(name = "last_error", length = 1000)
  private String lastError;

  @PrePersist
  void initializeNextCollection() {
    if (nextCollectAt == null) nextCollectAt = LocalDateTime.now(ZoneOffset.UTC);
  }

  public boolean isNewerThan(LocalDateTime attemptedAt) {
    return lastAttemptAt != null && lastAttemptAt.isAfter(attemptedAt);
  }

  /** nextCollectAt doubles as a short collection lease; no transaction spans the HTTP request. */
  public LocalDateTime claimIfDue(LocalDateTime now) {
    now = now.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    if (nextCollectAt != null && nextCollectAt.isAfter(now)) return null;
    LocalDateTime token = lastAttemptAt != null && !now.isAfter(lastAttemptAt)
        ? lastAttemptAt.plusNanos(1000) : now;
    lastAttemptAt = token;
    nextCollectAt = now.plusMinutes(10);
    return token;
  }

  public void recordSuccess(LocalDateTime attemptedAt, LocalDateTime completedAt) {
    this.lastAttemptAt = attemptedAt;
    this.lastSuccessAt = completedAt;
    this.nextCollectAt = completedAt.plusHours(6);
    this.consecutiveFailures = 0;
    this.lastError = null;
  }

  public void recordFailure(LocalDateTime attemptedAt, LocalDateTime completedAt, String error) {
    this.lastAttemptAt = attemptedAt;
    if (consecutiveFailures < Integer.MAX_VALUE) this.consecutiveFailures++;
    // 5, 10, 20, ... minutes, capped at 6 hours. No immediate retry loop.
    long delayMinutes = Math.min(360L, 5L << Math.min(consecutiveFailures - 1, 7));
    this.nextCollectAt = completedAt.plusMinutes(delayMinutes);
    this.lastError = error == null ? "News collection failed" : error.substring(0, Math.min(error.length(), 1000));
  }
}
