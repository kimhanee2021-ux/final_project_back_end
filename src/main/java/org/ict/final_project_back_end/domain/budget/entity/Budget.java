package org.ict.final_project_back_end.domain.budget.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.ict.final_project_back_end.domain.user.entity.User;

import java.time.LocalDateTime;

// 월 예산 설정 - AI 소비패턴. 사용자당 월 1건.
// 설계안의 year_month는 MySQL 예약어(YEAR_MONTH)라 budget_month로 이름을 바꿨다.
@Entity
@Table(name = "budgets", uniqueConstraints = @UniqueConstraint(name = "uq_budget_user_month", columnNames = {"user_id", "budget_month"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Budget {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  // "2026-10" 형식
  @Column(name = "budget_month", nullable = false, length = 7)
  private String budgetMonth;

  @Column(name = "limit_amount", nullable = false)
  private Integer limitAmount;

  @Builder.Default
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt = LocalDateTime.now();

  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  // ddl-auto로 만든 테이블에는 ON UPDATE CURRENT_TIMESTAMP가 없으므로 JPA가 수정 시각을 채운다
  @PreUpdate
  void touchUpdatedAt() {
    this.updatedAt = LocalDateTime.now();
  }
}
