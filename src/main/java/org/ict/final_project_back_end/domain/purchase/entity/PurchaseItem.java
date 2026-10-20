package org.ict.final_project_back_end.domain.purchase.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 거래 한 건에 담긴 상품 하나 (번들이면 여러 개). 스팀 구매내역에는 이름만 있어서 appId는 매칭으로 찾는다.
// DLC는 appId에 DLC 자신의 번호를, baseAppId에 본편 번호를 넣어 본편 게임의 지출로 합산한다.
@Entity
@Table(name = "purchase_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class PurchaseItem {

  // 보유 게임/게임 목록에서 이름이나 appId로 찾음
  public static final String MATCHED = "MATCHED";
  // 스팀에서 DLC로 확인되어 본편(baseAppId)에 연결됨
  public static final String DLC = "DLC";
  // 찾지 못함 (지출 합계에는 포함, 게임별 집계에서는 제외)
  public static final String UNMATCHED = "UNMATCHED";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "transaction_id", nullable = false)
  private PurchaseTransaction transaction;

  // 스팀 구매내역에 적힌 이름 그대로
  @Column(name = "raw_name", nullable = false, length = 500)
  private String rawName;

  @Column(name = "app_id")
  private Long appId;

  // DLC의 본편 appId. 본편 자체나 매칭 실패면 null.
  @Column(name = "base_app_id")
  private Long baseAppId;

  @Column(name = "match_status", nullable = false, length = 20)
  private String matchStatus;

  // 지출을 합산할 게임 (DLC는 본편, 그 외는 자기 자신)
  public Long targetAppId() {
    return baseAppId != null ? baseAppId : appId;
  }

  void attachTo(PurchaseTransaction transaction) {
    this.transaction = transaction;
  }
}
