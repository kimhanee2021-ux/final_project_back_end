package org.ict.final_project_back_end.domain.purchase.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.ict.final_project_back_end.domain.user.entity.User;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// 스팀 구매내역에서 가져온 거래 한 건 (구매/환불/게임 내 구매/선물 구매).
// 결제 원래 통화와 금액을 그대로 두고, 구매일 환율로 바꾼 원화 금액(amountKrw)을 함께 저장한다.
// 같은 파일을 다시 가져와도 중복되지 않도록 (user_id, external_key)에 유니크 제약을 건다.
@Entity
@Table(name = "purchase_transactions",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "external_key"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class PurchaseTransaction {

  public static final String PURCHASE = "PURCHASE";
  public static final String REFUND = "REFUND";
  public static final String IN_GAME = "IN_GAME";
  public static final String GIFT = "GIFT";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  // 프론트가 거래 ID를 해시해 만든 값 (거래 ID 원문은 저장하지 않음)
  @Column(name = "external_key", nullable = false, length = 32)
  private String externalKey;

  // 환불은 원래 구매와 같은 거래 ID를 쓴다 -> 같은 groupKey로 서로를 찾는다
  @Column(name = "group_key", nullable = false, length = 32)
  private String groupKey;

  @Column(name = "purchased_at", nullable = false)
  private LocalDate purchasedAt;

  @Column(nullable = false, length = 20)
  private String type;

  // 결제 원래 금액/통화. 원래 결제 수단으로 전액 환불된 환불 건은 금액이 없다(null).
  @Column(precision = 14, scale = 2)
  private BigDecimal amount;

  @Column(length = 3)
  private String currency;

  // 구매일 환율로 바꾼 원화 금액. 환율을 구할 수 없는 통화면 null.
  @Column(name = "amount_krw")
  private Integer amountKrw;

  // 적용한 환율(1 단위 = ? 원)과 그 환율의 기준일(주말이면 직전 영업일)
  @Column(name = "exchange_rate", precision = 14, scale = 6)
  private BigDecimal exchangeRate;

  @Column(name = "rate_date")
  private LocalDate rateDate;

  // 할인 전 원래 가격(원래 통화)과 할인율. 할인 없이 산 거래는 null.
  @Column(name = "original_amount", precision = 14, scale = 2)
  private BigDecimal originalAmount;

  @Column(name = "discount_percent")
  private Integer discountPercent;

  @Builder.Default
  @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderColumn(name = "item_order")
  private List<PurchaseItem> items = new ArrayList<>();

  @Builder.Default
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt = LocalDateTime.now();

  public void addItem(PurchaseItem item) {
    items.add(item);
    item.attachTo(this);
  }
}
