package org.ict.final_project_back_end.domain.recommendation.entity;

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

import java.time.LocalDateTime;

// 사용자에게 마지막으로 추천한 게임 한 건. 사용자당 최신 한 세트만 두고, 다시 계산하면 통째로 교체한다.
// 게임 이름·장르·썸네일은 조회할 때 games에서 읽으므로 저장하지 않는다.
// rank는 MySQL 예약어라 컬럼 이름을 rank_no로 쓴다.
@Entity
@Table(name = "recommended_games", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "rank_no"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class RecommendedGame {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "app_id", nullable = false)
  private Long appId;

  // 1부터 시작하는 순위
  @Column(name = "rank_no", nullable = false)
  private Integer rank;

  // 요청 안에서 순위를 매기는 값이다. 확률이나 일치율이 아니다.
  @Column(nullable = false)
  private Double score;

  // main = 기본 추천, new = 학습에 없던 게임(신작) 칸
  @Column(nullable = false, length = 10)
  private String source;

  // 이 추천을 만들 때 취향 계산에 실제로 쓴 게임 수 (한 세트의 모든 행이 같은 값)
  @Column(name = "used_game_count", nullable = false)
  private Integer usedGameCount;

  @Column(name = "generated_at", nullable = false)
  private LocalDateTime generatedAt;
}
