package org.ict.final_project_back_end.domain.aiPc.service;

import java.time.*;
import java.util.*;
import org.ict.final_project_back_end.domain.aiCommon.exception.AiException;
import org.ict.final_project_back_end.domain.aiPc.dto.PcPart;
import org.ict.final_project_back_end.domain.aiPc.entity.PcPartPriceSnapshot;
import org.ict.final_project_back_end.domain.aiPc.repository.PcPartPriceRepository;
import org.ict.final_project_back_end.domain.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class PcPartsService {
  private static final Set<String> CATEGORIES = Set.of("CPU", "GPU", "RAM", "SSD");
  // 저장된 가격은 6시간 동안 유효하다.
  private static final long PRICE_TTL_SECONDS = 21600;

  private final PcPartPriceRepository prices;
  private final UserRepository users;
  private final PcPriceSourceClient sources;

  public PcPartsService(PcPartPriceRepository p, UserRepository u, PcPriceSourceClient s) {
    prices = p;
    users = u;
    sources = s;
  }

  private void authenticated(Long userId) {
    if (userId == null || !users.existsById(userId))
      throw new AiException(HttpStatus.UNAUTHORIZED, "로그인 후 부품을 확인해주세요.");
  }

  private static boolean expired(PcPartPriceSnapshot snapshot) {
    return Instant.parse(snapshot.at()).isBefore(Instant.now().minusSeconds(PRICE_TTL_SECONDS));
  }

  public List<PcPart.Offer> offers(Long userId) {
    authenticated(userId);
    return collect();
  }

  // 진단은 외부 가격 갱신을 기다리지 않는다. 유효한 저장값만 사용하고 없으면 후보를 비운다.
  public List<PcPart.Offer> cachedOffers(Long userId) {
    authenticated(userId);
    var result = new ArrayList<PcPart.Offer>();
    for (var part : PcPartsCatalog.PARTS) {
      if (!CATEGORIES.contains(part.category())) continue;
      var quotes = new ArrayList<PcPart.RetailQuote>();
      for (String source : List.of("DANAWA", "COMTRIX")) {
        var history =
            prices.findTop2ByProductCodeAndSourceKeyOrderByIdDesc(part.productCode(), source);
        if (history.isEmpty()) continue;
        var current = history.getFirst();
        if (!Instant.parse(current.at()).isAfter(Instant.now().minusSeconds(PRICE_TTL_SECONDS))) continue;
        String url =
            "DANAWA".equals(source)
                ? part.priceSource()
                : PcRetailSources.comtrixUrl(part.productCode());
        if (url != null)
          quotes.add(
              new PcPart.RetailQuote(
                  source, url, current.price(), current.status(), current.note(), current.at()));
      }
      var valid =
          quotes.stream()
              .filter(
                  q -> "OBSERVED".equals(q.status()) && q.priceKrw() != null && q.priceKrw() > 0)
              .findFirst();
      if (valid.isPresent()) {
        var q = valid.get();
        result.add(
            new PcPart.Offer(
                part,
                q.priceKrw(),
                q.status(),
                q.note(),
                q.checkedAt(),
                null,
                null,
                null,
                quotes,
                q.url(),
                q.retailer()));
      }
    }
    return List.copyOf(result);
  }

  // 허용된 카탈로그 URL만 조회한다. 사용자/AI URL을 요청하지 않는다. 6시간 내에는 외부 요청 없이 저장값을 사용한다.
  private synchronized List<PcPart.Offer> collect() {
    var result = new ArrayList<PcPart.Offer>();
    for (var part : PcPartsCatalog.PARTS) {
      if (!CATEGORIES.contains(part.category())) continue;
      var history =
          prices.findTop2ByProductCodeAndSourceKeyOrderByIdDesc(part.productCode(), "DANAWA");
      PcPartPriceSnapshot current = history.isEmpty() ? null : history.get(0),
          previous = history.size() > 1 ? history.get(1) : null;
      if (current == null || expired(current)) {
        previous = current;
        current = fetch(part);
        prices.save(current);
      }
      Long previousPrice = previous == null ? null : previous.price();
      result.add(
          PcPartOffers.withRetailFallback(
              new PcPart.Offer(
                  part,
                  current.price(),
                  current.status(),
                  current.note(),
                  current.at(),
                  previousPrice,
                  previous == null ? null : previous.at(),
                  PcPartPrices.dropPercent(previousPrice, current.price()),
                  retailQuotes(part))));
    }
    return List.copyOf(result);
  }

  private List<PcPart.RetailQuote> retailQuotes(PcPart part) {
    String url = PcRetailSources.comtrixUrl(part.productCode());
    if (url == null) return List.of();
    var history =
        prices.findTop2ByProductCodeAndSourceKeyOrderByIdDesc(part.productCode(), "COMTRIX");
    var current = history.isEmpty() ? null : history.getFirst();
    if (current == null || expired(current)) {
      String at = Instant.now().toString();
      PcPartPrices.Parsed parsed;
      try {
        parsed = PcRetailPrices.parseComtrix(sources.fetchPage(url), part.name());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        parsed = new PcPartPrices.Parsed(null, "FAILED", "판매처 조회가 중단됐어요.");
      } catch (Exception e) {
        parsed = new PcPartPrices.Parsed(null, "FAILED", "판매처 가격을 확인하지 못했어요.");
      }
      current =
          prices.save(
              new PcPartPriceSnapshot(
                  part.productCode(),
                  "COMTRIX",
                  parsed.price(),
                  parsed.status(),
                  parsed.note(),
                  at));
    }
    return List.of(
        new PcPart.RetailQuote(
            "COMTRIX", url, current.price(), current.status(), current.note(), current.at()));
  }

  private PcPartPriceSnapshot fetch(PcPart part) {
    String at = Instant.now().toString();
    try {
      var value = PcPartPrices.parse(sources.fetchPage(part.priceSource()), part.name());
      return new PcPartPriceSnapshot(
          part.productCode(), value.price(), value.status(), value.note(), at);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return new PcPartPriceSnapshot(
          part.productCode(), null, "FAILED", "조회가 중단됐어요. 원문에서 확인해 주세요.", at);
    } catch (Exception e) {
      return new PcPartPriceSnapshot(
          part.productCode(), null, "FAILED", "현재 가격을 확인하지 못했어요. 원문에서 확인해 주세요.", at);
    }
  }
}
