package org.ict.final_project_back_end.domain.game.service;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import static org.ict.final_project_back_end.global.constant.SteamUrls.STEAM_STORE_BASE_URL;

// 스팀 상점 appdetails API(한국 상점, cc=kr) 호출만 담당한다. 실패하면 RestClientException을 그대로 던지고,
// 재시도/건너뛰기 같은 정책은 호출하는 쪽(가격 시드, 보유 게임 가격 보충)이 정한다.
@Component
public class SteamPriceClient {

  // filters=price_overview를 붙이면 appId 여러 개를 한 번에 물을 수 있다 (100개씩 묶어서 사용)
  public static final int MAX_BATCH_SIZE = 100;

  private final RestClient restClient = RestClient.create();

  // 응답 키는 appId 문자열이다. 가격 정보가 없는 게임(무료 등)은 data가 빈 배열([])로 온다.
  public Map<String, AppPrice> fetchPrices(Collection<Long> appIds) {
    URI uri = UriComponentsBuilder.fromUriString(STEAM_STORE_BASE_URL + "/api/appdetails")
        .queryParam("cc", "kr")
        .queryParam("filters", "price_overview")
        .queryParam("appids", appIds.stream().map(String::valueOf).collect(Collectors.joining(",")))
        .build(true)
        .toUri();
    Map<String, AppPrice> body = restClient.get().uri(uri).retrieve().body(new ParameterizedTypeReference<>() {});
    return body == null ? Map.of() : body;
  }

  // 가격 정보가 없는 게임이 진짜 무료인지 확인한다. is_free는 filters 없이 물어야 내려오므로 한 개씩만 묻는다.
  // 알 수 없으면 null.
  public Boolean fetchIsFree(Long appId) {
    URI uri = UriComponentsBuilder.fromUriString(STEAM_STORE_BASE_URL + "/api/appdetails")
        .queryParam("cc", "kr")
        .queryParam("appids", appId)
        .build(true)
        .toUri();
    Map<String, AppPrice> body = restClient.get().uri(uri).retrieve().body(new ParameterizedTypeReference<>() {});
    AppPrice app = body == null ? null : body.get(String.valueOf(appId));
    if (app == null || !app.success() || !(app.data() instanceof Map<?, ?> data)) {
      return null;
    }
    return Boolean.TRUE.equals(data.get("is_free"));
  }

  // data는 객체 또는 빈 배열이라 Object로 받아 직접 꺼낸다
  public record AppPrice(boolean success, Object data) {

    public PriceOverview priceOverview() {
      if (!(data instanceof Map<?, ?> map) || !(map.get("price_overview") instanceof Map<?, ?> p)) {
        return null;
      }
      return new PriceOverview((String) p.get("currency"), toInt(p.get("initial")), toInt(p.get("final")),
          toInt(p.get("discount_percent")));
    }

    private static int toInt(Object value) {
      return value instanceof Number n ? n.intValue() : 0;
    }
  }

  // initial/final은 실제 금액의 100배 값이다 (1600000 = 16,000원)
  public record PriceOverview(String currency, int initial, int finalPrice, int discountPercent) {
  }
}
