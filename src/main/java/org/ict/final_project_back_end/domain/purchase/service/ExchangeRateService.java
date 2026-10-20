package org.ict.final_project_back_end.domain.purchase.service;

import lombok.extern.slf4j.Slf4j;
import org.ict.final_project_back_end.domain.purchase.entity.ExchangeRate;
import org.ict.final_project_back_end.domain.purchase.repository.ExchangeRateRepository;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// 구매일 기준 "1 단위 = ? 원" 환율을 구한다. Frankfurter(유럽중앙은행 기준, 무료, 키 없음)를 쓰고,
// 한 번 구한 통화·날짜는 DB에 저장해 다시 묻지 않는다. 지원하지 않는 통화면 빈 값을 돌려준다.
@Slf4j
@Service
public class ExchangeRateService {

  private static final String RATE_URL = "https://api.frankfurter.dev/v1/{date}?from={currency}&to=KRW";
  // 유럽중앙은행이 고시하는 통화 (스팀 통화 중 TWD, VND, UAH, KZT 등은 없음)
  static final Set<String> SUPPORTED = Set.of("AUD", "BRL", "CAD", "CHF", "CNY", "CZK", "DKK", "EUR", "GBP", "HKD",
      "HUF", "IDR", "ILS", "INR", "ISK", "JPY", "MXN", "MYR", "NOK", "NZD", "PHP", "PLN", "RON", "SEK", "SGD", "THB",
      "TRY", "USD", "ZAR");

  public record Rate(BigDecimal rateToKrw, LocalDate rateDate) {
  }

  private final ExchangeRateRepository repository;
  private final RestClient restClient = RestClient.create();

  public ExchangeRateService(ExchangeRateRepository repository) {
    this.repository = repository;
  }

  public Optional<Rate> rateToKrw(String currency, LocalDate date) {
    if ("KRW".equals(currency)) {
      return Optional.of(new Rate(BigDecimal.ONE, date));
    }
    if (currency == null || !SUPPORTED.contains(currency)) {
      return Optional.empty();
    }
    Optional<ExchangeRate> cached = repository.findByCurrencyAndRequestedDate(currency, date);
    if (cached.isPresent()) {
      return cached.map(rate -> new Rate(rate.getRateToKrw(), rate.getRateDate()));
    }
    try {
      Map<String, Object> body = restClient.get().uri(RATE_URL, date, currency).retrieve()
          .body(new ParameterizedTypeReference<>() {});
      if (body == null || !(body.get("rates") instanceof Map<?, ?> rates) || !(rates.get("KRW") instanceof Number krw)) {
        return Optional.empty();
      }
      Rate rate = new Rate(new BigDecimal(krw.toString()), LocalDate.parse(String.valueOf(body.get("date"))));
      save(currency, date, rate);
      return Optional.of(rate);
    } catch (RestClientException | IllegalArgumentException e) {
      log.warn("환율 조회 실패 {} {}", currency, date);
      return Optional.empty();
    }
  }

  private void save(String currency, LocalDate date, Rate rate) {
    try {
      repository.save(ExchangeRate.builder().currency(currency).requestedDate(date)
          .rateDate(rate.rateDate()).rateToKrw(rate.rateToKrw()).build());
    } catch (DataIntegrityViolationException ignored) {
      // 동시에 같은 환율을 저장한 경우: 이미 있으니 그대로 둔다
    }
  }
}
