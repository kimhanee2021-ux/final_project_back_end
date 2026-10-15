package org.ict.final_project_back_end.domain.purchase.entity;

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

import java.math.BigDecimal;
import java.time.LocalDate;

// 통화별·날짜별 원화 환율 캐시 (1 단위 = rate 원). 같은 날짜·통화로 환율 API를 다시 부르지 않기 위해 저장한다.
// requestedDate는 물어본 날짜(구매일), rateDate는 실제로 적용된 날짜(주말·공휴일이면 직전 영업일).
@Entity
@Table(name = "exchange_rates", uniqueConstraints = @UniqueConstraint(columnNames = {"currency", "requested_date"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ExchangeRate {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "requested_date", nullable = false)
  private LocalDate requestedDate;

  @Column(name = "rate_date", nullable = false)
  private LocalDate rateDate;

  @Column(name = "rate_to_krw", nullable = false, precision = 14, scale = 6)
  private BigDecimal rateToKrw;
}
