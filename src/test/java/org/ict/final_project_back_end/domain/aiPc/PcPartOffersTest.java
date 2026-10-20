package org.ict.final_project_back_end.domain.aiPc;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.ict.final_project_back_end.domain.aiPc.dto.PcPart;
import org.ict.final_project_back_end.domain.aiPc.service.PcPartOffers;
import org.junit.jupiter.api.Test;

class PcPartOffersTest {
  private final PcPart part = new PcPart("파워", "정확한 제품", 1, "https://example.invalid", List.of());

  private PcPart.Offer primary(Long price, String status, PcPart.RetailQuote quote) {
    return new PcPart.Offer(
        part,
        price,
        status,
        "주 가격",
        "2026-10-19T00:00:00Z",
        80000L,
        "2026-10-18T00:00:00Z",
        10,
        List.of(quote));
  }

  private PcPart.RetailQuote quote(Long price, String status) {
    return new PcPart.RetailQuote(
        "COMTRIX",
        "https://comtrix.kr/product/test",
        price,
        status,
        "판매처 가격",
        "2026-10-19T01:00:00Z");
  }

  @Test
  void fallbackKeepsActualSourceAndClearsCrossRetailerDrop() {
    var offer =
        PcPartOffers.withRetailFallback(primary(null, "UNAVAILABLE", quote(67800L, "OBSERVED")));
    assertEquals(67800L, offer.priceKrw());
    assertEquals("COMTRIX", offer.priceSourceName());
    assertEquals("https://comtrix.kr/product/test", offer.priceSourceUrl());
    assertNull(offer.previousPriceKrw());
    assertNull(offer.observedDropPercent());
  }

  @Test
  void validPrimaryIsNotReplacedByCheaperAlternate() {
    var original = primary(70000L, "OBSERVED", quote(67800L, "OBSERVED"));
    assertSame(original, PcPartOffers.withRetailFallback(original));
  }

  @Test
  void invalidAlternateCannotBecomeRecommendationEvidence() {
    for (var quote :
        List.of(quote(0L, "OBSERVED"), quote(null, "FAILED"), quote(67800L, "UNVERIFIED"))) {
      var original = primary(null, "FAILED", quote);
      assertSame(original, PcPartOffers.withRetailFallback(original));
    }
  }
}
