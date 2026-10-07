package org.ict.final_project_back_end.domain.analysis.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.ict.final_project_back_end.domain.library.entity.LibraryGame;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 구매 계획(플레이 약속·정리 예정일) - 가치판단 도우미. 보유 게임 id를 그대로 PK로 쓴다.
@Entity
@Table(name = "purchase_plans")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class PurchasePlan {

  @Id
  @Column(name = "library_game_id")
  private Long libraryGameId;

  @MapsId
  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "library_game_id")
  private LibraryGame libraryGame;

  @Builder.Default
  @Column(name = "pledge_type", nullable = false, length = 20)
  private String pledgeType = "NONE";

  @Column(name = "planned_playtime_minutes")
  private Integer plannedPlaytimeMinutes;

  @Column(name = "planned_exit_date")
  private LocalDate plannedExitDate;

  @Builder.Default
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt = LocalDateTime.now();
}
