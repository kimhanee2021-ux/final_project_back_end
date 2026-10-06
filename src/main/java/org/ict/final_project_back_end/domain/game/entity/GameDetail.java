package org.ict.final_project_back_end.domain.game.entity;

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

// 스팀 스토어에서 가져온 게임 상세정보 캐시. 하루가 지나면 다시 가져온다 (fetchedAt 기준).
@Entity
@Table(name = "game_details", uniqueConstraints = @UniqueConstraint(columnNames = {"app_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class GameDetail {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "app_id", nullable = false)
  private Long appId;

  @Column(length = 50)
  private String type;

  @Column(name = "is_free")
  private Boolean free;

  @Column(name = "short_description", length = 2000)
  private String shortDescription;

  @Column(name = "header_image_url", length = 500)
  private String headerImageUrl;

  // 여러 명일 수 있어 줄바꿈으로 구분해 한 컬럼에 담는다
  @Column(length = 1000)
  private String developers;

  @Column(length = 1000)
  private String publishers;

  // developers와 같은 방식으로 줄바꿈 구분
  @Column(length = 1000)
  private String genres;

  @Column(name = "release_date", length = 100)
  private String releaseDate;

  // 가격은 스팀이 주는 최소 단위(센트, 원 등) 정수 그대로 저장한다. 통화는 currency 기준.
  @Column(length = 10)
  private String currency;

  @Column(name = "initial_price")
  private Integer initialPrice;

  @Column(name = "final_price")
  private Integer finalPrice;

  @Column(name = "discount_percent")
  private Integer discountPercent;

  @Column(name = "fetched_at", nullable = false)
  private LocalDateTime fetchedAt;

  public void update(String type, Boolean free, String shortDescription, String headerImageUrl,
                     String developers, String publishers, String genres, String releaseDate) {
    this.type = type;
    this.free = free;
    this.shortDescription = shortDescription;
    this.headerImageUrl = headerImageUrl;
    this.developers = developers;
    this.publishers = publishers;
    this.genres = genres;
    this.releaseDate = releaseDate;
    this.fetchedAt = LocalDateTime.now();
  }

  // 가격 정보가 없는 게임(무료 등)은 null로 덮어써서 예전 가격이 남지 않게 한다
  public void updatePrice(String currency, Integer initialPrice, Integer finalPrice, Integer discountPercent) {
    this.currency = currency;
    this.initialPrice = initialPrice;
    this.finalPrice = finalPrice;
    this.discountPercent = discountPercent;
  }
}
