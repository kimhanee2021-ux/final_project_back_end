package org.ict.final_project_back_end.domain.analysis.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.ict.final_project_back_end.domain.library.entity.LibraryGame;

import java.time.LocalDateTime;

// 포기 기록(어디서 왜 그만뒀는지) - 선택이유 모아두기.
// app_id는 게임별 집계용으로 보유 게임과 별개로 한 번 더 들고 있는다.
@Entity
@Table(name = "game_dropouts", indexes = @Index(name = "idx_dropout_app", columnList = "app_id, playtime_at_drop_minutes"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class GameDropout {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "library_game_id", nullable = false, unique = true)
  private LibraryGame libraryGame;

  @Column(name = "app_id", nullable = false)
  private Long appId;

  @Column(name = "playtime_at_drop_minutes", nullable = false)
  private Integer playtimeAtDropMinutes;

  @Column(name = "reason_code", nullable = false, length = 20)
  private String reasonCode;

  @Column(name = "reason_text", length = 200)
  private String reasonText;

  @Builder.Default
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt = LocalDateTime.now();
}
