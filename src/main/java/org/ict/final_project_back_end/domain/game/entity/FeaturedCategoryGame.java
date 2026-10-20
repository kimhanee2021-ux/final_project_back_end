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

// 스팀 상점(/api/featuredcategories?cc=kr&l=korean) 카테고리별(특가/인기 상품/신규 출시 등) 캐시.
// 카테고리 키는 스팀이 내려주는 그대로 쓰며 고정 스키마가 아니라서(스팀이 카테고리를 추가/제거할 수 있음) enum이 아닌 문자열로 둔다.
@Entity
@Table(name = "featured_category_games")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class FeaturedCategoryGame {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 스팀 응답의 카테고리 키 (예: specials, top_sellers, new_releases, coming_soon)
  @Column(name = "category_key", nullable = false, length = 50)
  private String categoryKey;

  // 스팀이 내려주는 로케일(한국어) 카테고리 이름 (예: "특별 할인")
  @Column(name = "category_name", nullable = false, length = 100)
  private String categoryName;

  // 응답 JSON에 카테고리가 등장한 순서 (화면에 섹션을 나열할 때 사용)
  @Column(name = "category_order", nullable = false)
  private Integer categoryOrder;

  @Column(name = "app_id", nullable = false)
  private Long appId;

  // 카테고리 안에서의 노출 순서(0부터)
  @Column(name = "rank_order", nullable = false)
  private Integer rankOrder;

  @Column(nullable = false, length = 255)
  private String name;

  private Boolean discounted;

  @Column(name = "discount_percent")
  private Integer discountPercent;

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
