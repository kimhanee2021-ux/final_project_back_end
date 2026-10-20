package org.ict.final_project_back_end.domain.purchase.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// 프론트가 스팀 구매내역 HTML을 브라우저에서 파싱해 보내는 거래 목록.
// 결제 수단·지갑 잔액·거래 ID 원문은 담지 않는다(거래 ID는 해시값 externalKey/groupKey로만 온다).
public record PurchaseImportRequest(
    @NotNull @Size(max = 5000) List<@Valid Transaction> transactions
) {

  public record Transaction(
      @NotBlank @Size(max = 32) String externalKey,
      @NotBlank @Size(max = 32) String groupKey,
      @NotNull LocalDate purchasedAt,
      @NotBlank @Pattern(regexp = "PURCHASE|REFUND|IN_GAME|GIFT") String type,
      @NotEmpty @Size(max = 50) List<@NotBlank @Size(max = 500) String> items,
      Long appId,
      // 원래 결제 수단으로 전액 환불된 환불 건은 금액이 없다
      @DecimalMin("0") BigDecimal amount,
      @Size(max = 3) String currency,
      @Min(0) @Max(100) Integer discountPercent,
      @DecimalMin("0") BigDecimal originalAmount
  ) {
  }
}
