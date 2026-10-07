package org.ict.final_project_back_end.domain.game.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// 스팀 게임 카탈로그(1일 1회 캐싱). PK는 스팀 appId를 그대로 쓴다.
@Entity
@Table(name = "games")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Game {

  @Id
  @Column(name = "app_id")
  private Long appId;

  @Column(nullable = false)
  private String name;

  private String genre;

  @Column(name = "price_info", length = 100)
  private String priceInfo;

  @Column(name = "discount_percent")
  private Integer discountPercent;

  @Column(name = "review_count")
  private Integer reviewCount;

  @Column(name = "positive_rate", precision = 5, scale = 2)
  private BigDecimal positiveRate;

  @Column(name = "min_spec_raw_text", columnDefinition = "TEXT")
  private String minSpecRawText;

  @Column(name = "rec_spec_raw_text", columnDefinition = "TEXT")
  private String recSpecRawText;

  @Column(name = "thumbnail_url", length = 500)
  private String thumbnailUrl;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(name = "last_synced_at")
  private LocalDateTime lastSyncedAt;
}
