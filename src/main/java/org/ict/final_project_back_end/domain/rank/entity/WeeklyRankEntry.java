package org.ict.final_project_back_end.domain.rank.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

// Steam 원본 순위와 서비스에서 노출할 게임 순위(1~5)를 별도로 보관한다.
@Entity
@Table(name = "rank_weekly_entries", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"chart_id", "app_id"}),
    @UniqueConstraint(columnNames = {"chart_id", "game_rank"})
})
@Check(constraints = "game_rank BETWEEN 1 AND 5")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class WeeklyRankEntry {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "rank_id")
  private Long rankId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "chart_id", nullable = false)
  private WeeklyRankChart chart;

  // games에 없는 앱도 순위에 포함되므로 Game 연관관계/FK를 만들지 않는다.
  @Column(name = "app_id", nullable = false)
  private Long appId;

  @Column(name = "game_name_snapshot", length = 500)
  private String gameNameSnapshot;

  @Column(name = "steam_rank", nullable = false)
  private Integer steamRank;

  @JdbcTypeCode(SqlTypes.TINYINT)
  @Column(name = "game_rank", nullable = false, columnDefinition = "TINYINT")
  private Integer gameRank;

  @Column(name = "last_week_rank")
  private Integer lastWeekRank;

  @Column(name = "consecutive_weeks")
  private Integer consecutiveWeeks;
}
