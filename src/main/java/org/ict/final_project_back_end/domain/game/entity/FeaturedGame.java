package org.ict.final_project_back_end.domain.game.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 스팀 홈(/api/featured) 추천 캐시. featured_win 섹션만 저장한다(Windows 기준 추천 목록).
// 매일 스케줄러가 전체를 비우고 다시 채우는 스냅샷이라 노출 순서(rankOrder)까지 그대로 저장한다.
@Entity
@Table(name = "featured_games")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class FeaturedGame {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "app_id", nullable = false)
  private Long appId;

  // 노출 순서(0부터). 스팀이 내려주는 배열 순서를 그대로 보존한다.
  @Column(name = "rank_order", nullable = false)
  private Integer rankOrder;

  @Column(nullable = false, length = 255)
  private String name;

  private Boolean discounted;

  @Column(name = "discount_percent")
  private Integer discountPercent;

  // 금액 단위는 스팀 원본 그대로(원화는 100배 값, 예: 1600000 = 16,000원)
  @Column(name = "original_price")
  private Integer originalPrice;

  @Column(name = "final_price")
  private Integer finalPrice;

  @Column(length = 10)
  private String currency;

  @Column(name = "header_image", length = 500)
  private String headerImage;

  @Column(name = "fetched_at", nullable = false)
  private LocalDateTime fetchedAt;
}
