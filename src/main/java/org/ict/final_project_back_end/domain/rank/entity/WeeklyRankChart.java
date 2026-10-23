package org.ict.final_project_back_end.domain.rank.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 이미 생성된 국가별 주간 차트. 출처/검증 상태는 저장된 값을 그대로 제공한다.
@Entity
@Table(name = "rank_weekly_charts",
    uniqueConstraints = @UniqueConstraint(columnNames = {"country_code", "week_start"}))
@Check(constraints = "week_end_exclusive > week_start")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class WeeklyRankChart {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "chart_id")
  private Long chartId;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "country_code", length = 2, columnDefinition = "CHAR(2)", nullable = false)
  private String countryCode;

  @Column(name = "week_start", nullable = false)
  private LocalDate weekStart;

  @Column(name = "week_end_exclusive", nullable = false)
  private LocalDate weekEndExclusive;

  @Column(name = "source_type", length = 30, nullable = false)
  private String sourceType;

  @Column(name = "verification_status", length = 20, nullable = false)
  private String verificationStatus;

  @Column(name = "source_url", length = 500)
  private String sourceUrl;

  @Column(name = "fetched_at", columnDefinition = "DATETIME(6)")
  private LocalDateTime fetchedAt;
}
