package org.ict.final_project_back_end.domain.aiPc.service;

import com.fasterxml.jackson.databind.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;

/** COMTRIX 공개 상품 JSON-LD와 가격 메타값을 교차 확인한다. 임의 판매처에는 적용하지 않는다. */
public final class PcRetailPrices {
  private PcRetailPrices() {}

  public static PcPartPrices.Parsed parseComtrix(String html, String expectedName) {
    try {
      var products = new ArrayList<JsonNode>();
      var scripts =
          Pattern.compile(
                  "(?is)<script\\b[^>]*type\\s*=\\s*[\"']application/ld\\+json[\"'][^>]*>(.*?)</script>")
              .matcher(html);
      var mapper = new ObjectMapper();
      while (scripts.find()) collect(mapper.readTree(scripts.group(1)), products);
      var matching =
          products.stream()
              .filter(
                  p ->
                      normalize(p.path("name").asText().replaceFirst("\\s*-\\s*COMTRIX$", ""))
                          .equals(normalize(expectedName)))
              .toList();
      if (matching.size() != 1) throw new IllegalArgumentException();
      var offer = matching.getFirst().path("offers");
      if (!"Offer".equals(offer.path("@type").asText())
          || !"KRW".equals(offer.path("priceCurrency").asText()))
        throw new IllegalArgumentException();
      if (!Set.of("https://schema.org/InStock", "http://schema.org/InStock")
          .contains(offer.path("availability").asText()))
        return unavailable("판매처 재고 표기를 확인하지 못했어요.");
      if (!Set.of(
              "NewCondition", "https://schema.org/NewCondition", "http://schema.org/NewCondition")
          .contains(offer.path("itemCondition").asText())) throw new IllegalArgumentException();
      String until = offer.path("priceValidUntil").asText();
      if (until.isBlank()
          || LocalDate.parse(until).isBefore(LocalDate.now(ZoneId.of("Asia/Seoul"))))
        return unavailable("가격 유효기간을 확인하지 못했어요.");
      long price = new BigDecimal(offer.path("price").asText()).longValueExact();
      var metas = Pattern.compile("(?is)<meta\\b[^>]*>").matcher(html);
      String amount = "", currency = "";
      while (metas.find()) {
        String tag = metas.group();
        var value = Pattern.compile("(?is)content\\s*=\\s*([\"'])(.*?)\\1").matcher(tag);
        if (!value.find()) continue;
        if (tag.matches("(?is).*property\\s*=\\s*[\"']product:price:amount[\"'].*"))
          amount = value.group(2);
        if (tag.matches("(?is).*property\\s*=\\s*[\"']product:price:currency[\"'].*"))
          currency = value.group(2);
      }
      if (price <= 0
          || price > 100000000
          || !currency.equals("KRW")
          || new BigDecimal(amount).longValueExact() != price) throw new IllegalArgumentException();
      return new PcPartPrices.Parsed(
          price, "OBSERVED", "COMTRIX 공개 일반 판매가. 배송비·최종 결제금액·실재고는 판매처에서 확인해 주세요.");
    } catch (Exception failure) {
      return unavailable("상품·가격·재고 근거가 일치하지 않아 판매가를 표시하지 않아요.");
    }
  }

  private static void collect(JsonNode node, List<JsonNode> products) {
    if (node == null) return;
    if (node.isArray()) {
      node.forEach(n -> collect(n, products));
      return;
    }
    if (node.isObject()) {
      if ("Product".equals(node.path("@type").asText())) products.add(node);
      if (node.has("@graph")) collect(node.get("@graph"), products);
    }
  }

  private static String normalize(String value) {
    return value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
  }

  private static PcPartPrices.Parsed unavailable(String note) {
    return new PcPartPrices.Parsed(null, "UNVERIFIED", note);
  }
}
