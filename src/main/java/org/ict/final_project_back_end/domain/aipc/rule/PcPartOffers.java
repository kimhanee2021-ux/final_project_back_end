package org.ict.final_project_back_end.domain.aipc.rule;

import org.ict.final_project_back_end.domain.aipc.dto.PcPart;

/** 주 판매처를 확인할 수 없을 때만 검증된 대체 판매처를 사용한다. 판매처 간 할인율을 계산하지 않는다. */
public final class PcPartOffers {
  private PcPartOffers() {}

  public static PcPart.Offer withRetailFallback(PcPart.Offer primary) {
    if ("OBSERVED".equals(primary.status()) && primary.priceKrw() != null && primary.priceKrw() > 0)
      return primary;
    for (var quote : primary.retailQuotes())
      if ("OBSERVED".equals(quote.status()) && quote.priceKrw() != null && quote.priceKrw() > 0) {
        return new PcPart.Offer(
            primary.part(),
            quote.priceKrw(),
            quote.status(),
            quote.note(),
            quote.checkedAt(),
            null,
            null,
            null,
            primary.retailQuotes(),
            quote.url(),
            quote.retailer());
      }
    return primary;
  }
}
