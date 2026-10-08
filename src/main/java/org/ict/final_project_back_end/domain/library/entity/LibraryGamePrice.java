package org.ict.final_project_back_end.domain.library.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 라이브러리 게임의 원화(KRW) 가격 캐시. games.price_info는 달러 기준 원본 텍스트라 그대로 쓸 수 없어서,
// 스팀 스토어 appdetails API(cc=kr)로 받아온 값을 appId 기준으로 별도 저장하고 1일 주기로 캐싱한다.
@Entity
@Table(name = "library_game_prices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class LibraryGamePrice {

  @Id
  @Column(name = "app_id")
  private Long appId;

  @Column(name = "is_free", nullable = false)
  private Boolean isFree;

  @Column(name = "original_price_krw")
  private Integer originalPriceKrw;

  @Column(name = "current_price_krw")
  private Integer currentPriceKrw;

  @Column(name = "discount_percent")
  private Integer discountPercent;

  @Column(name = "checked_at", nullable = false)
  private LocalDateTime checkedAt;

  // 스팀 스토어에서 새로 받아온 값으로 캐시를 갱신한다 (appId는 식별 정보라 제외)
  public void update(Boolean isFree, Integer originalPriceKrw, Integer currentPriceKrw,
                      Integer discountPercent, LocalDateTime checkedAt) {
    this.isFree = isFree;
    this.originalPriceKrw = originalPriceKrw;
    this.currentPriceKrw = currentPriceKrw;
    this.discountPercent = discountPercent;
    this.checkedAt = checkedAt;
  }
}
