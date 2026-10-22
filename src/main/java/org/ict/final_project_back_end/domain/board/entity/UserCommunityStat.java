package org.ict.final_project_back_end.domain.board.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.ict.final_project_back_end.domain.user.entity.User;

import java.time.LocalDateTime;

// 커뮤니티 등업/레벨/포인트. 사용자당 1건이라 user_id를 PK로 쓴다.
@Entity
@Table(name = "user_community_stats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class UserCommunityStat {

  @Id
  @Column(name = "user_id")
  private Long userId;

  @MapsId
  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id")
  private User user;

  @Builder.Default
  @Column(name = "activity_points", nullable = false)
  private Integer activityPoints = 0;

  @Builder.Default
  @Column(nullable = false)
  private Integer level = 1;

  @Builder.Default
  @Column(name = "is_promoted", nullable = false, columnDefinition = "TINYINT(1)")
  private Boolean promoted = false;

  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  // ddl-auto로 만든 테이블에는 ON UPDATE CURRENT_TIMESTAMP가 없으므로 JPA가 수정 시각을 채운다
  @PreUpdate
  void touchUpdatedAt() {
    this.updatedAt = LocalDateTime.now();
  }
}
