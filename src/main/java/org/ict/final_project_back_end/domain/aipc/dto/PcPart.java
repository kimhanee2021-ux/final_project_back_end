package org.ict.final_project_back_end.domain.aipc.dto;

import java.util.List;

public record PcPart(
    String category,
    String name,
    long productCode,
    String manufacturerSource,
    List<String> compatibilityChecks) {
  public String priceSource() {
    return "https://prod.danawa.com/info/?pcode=" + productCode;
  }

  public record RetailQuote(
      String retailer, String url, Long priceKrw, String status, String note, String checkedAt) {}

  public record Offer(
      PcPart part,
      Long priceKrw,
      String status,
      String note,
      String checkedAt,
      Long previousPriceKrw,
      String previousCheckedAt,
      Integer observedDropPercent,
      List<RetailQuote> retailQuotes,
      String priceSourceUrl,
      String priceSourceName) {
    public Offer(
        PcPart part,
        Long price,
        String status,
        String note,
        String at,
        Long previous,
        String previousAt,
        Integer drop) {
      this(part, price, status, note, at, previous, previousAt, drop, List.of());
    }

    public Offer(
        PcPart part,
        Long price,
        String status,
        String note,
        String at,
        Long previous,
        String previousAt,
        Integer drop,
        List<RetailQuote> quotes) {
      this(
          part,
          price,
          status,
          note,
          at,
          previous,
          previousAt,
          drop,
          quotes,
          part.priceSource(),
          "다나와");
    }
  }
}
