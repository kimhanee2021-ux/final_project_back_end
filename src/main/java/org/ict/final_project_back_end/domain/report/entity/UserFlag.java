package org.ict.final_project_back_end.domain.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.ict.final_project_back_end.domain.user.entity.User;

import java.time.LocalDateTime;

// 관리자의 나쁜 언어 사용자 표시 - 비속어 필터링(관리자 기능). released_at이 채워지면 해제된 상태.
@Entity
@Table(name = "user_flags")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class UserFlag {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Builder.Default
  @Column(name = "flag_type", nullable = false, length = 30)
  private String flagType = "ABUSIVE_LANGUAGE";

  @Column(length = 500)
  private String reason;

  // 신고를 근거로 표시한 경우에만 채워진다
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "report_id")
  private Report report;

  // 표시한 관리자
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "flagged_by", nullable = false)
  private User flaggedBy;

  @Builder.Default
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt = LocalDateTime.now();

  @Column(name = "released_at")
  private LocalDateTime releasedAt;
}
