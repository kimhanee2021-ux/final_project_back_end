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

import java.time.LocalDateTime;

// 스팀 한국 상점 가격(원화) 캐시. games.price_info는 CSV의 달러 가격이라 소비 분석에는 이 값을 쓴다.
// 한국 가격은 환율이 아니라 지역별로 따로 매겨지므로(예: $59.99 -> 64,800원) 스팀에서 직접 받아 저장한다.
@Entity
@Table(name = "game_prices_krw")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class GamePriceKrw {

  public static final String PRICED = "PRICED";
  // 무료 게임 (가격 정보가 없어서 is_free까지 확인한 경우, 금액 0원)
  public static final String FREE = "FREE";
  // 가격 정보 없음 (무료 여부를 아직 확인하지 못했거나 판매가가 없는 게임)
  public static final String NO_PRICE = "NO_PRICE";
  // 한국 상점에서 제공하지 않는 게임
  public static final String UNAVAILABLE = "UNAVAILABLE";

  @Id
  @Column(name = "app_id")
  private Long appId;

  @Column(nullable = false, length = 20)
  private String status;

  // 한국 정가(원). 할인과 무관한 기준 가격이라 소비 추정에는 이 값을 쓴다.
  @Column(name = "initial_krw")
  private Integer initialKrw;

  // 조회 시점 판매가(원, 할인 반영)
  @Column(name = "final_krw")
  private Integer finalKrw;

  @Column(name = "discount_percent")
  private Integer discountPercent;

  @Column(name = "fetched_at", nullable = false)
  private LocalDateTime fetchedAt;

  // NO_PRICE인 게임에 대해 무료 여부(is_free)까지 확인했는지. null/false면 아직 확인 전이다.
  // (12만 건 시드나 가격 갱신은 묶음 조회라 is_free를 모르므로, 보유 게임 조회 때 따로 확인한다)
  @Column(name = "free_checked")
  private Boolean freeChecked;

  public boolean needsFreeCheck() {
    return NO_PRICE.equals(status) && !Boolean.TRUE.equals(freeChecked);
  }

  // 무료 여부 확인 결과를 반영한다. 무료면 FREE(0원), 아니면 NO_PRICE로 두되 다시 확인하지 않도록 표시한다.
  public void applyFreeCheck(boolean free) {
    this.freeChecked = true;
    if (free) {
      this.status = FREE;
      this.initialKrw = 0;
      this.finalKrw = 0;
      this.discountPercent = 0;
    }
  }
}
