package org.ict.final_project_back_end.domain.game.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

// 스팀 IStoreBrowseService/GetItems API(한국 상점) 호출만 담당한다. 키 없이 appId 여러 개의 현재가·할인율을 한 번에 받는다.
// 실패하면 RestClientException을 그대로 던지고, 재시도/중단 정책은 호출하는 쪽(GamePriceSyncService)이 정한다.
@Component
public class SteamStoreBrowseClient {

  private static final String GET_ITEMS_URL = "https://api.steampowered.com/IStoreBrowseService/GetItems/v1";

  // 한 요청에 200개까지는 되고 400개부터 400 오류가 나서 여유 있게 100개씩 묶어 쓴다
  public static final int MAX_BATCH_SIZE = 100;

  // 응답 항목의 type 값. 0이 게임이고 DLC는 4 등 다른 값이라 게임만 남긴다
  private static final int GAME_TYPE = 0;

  private final RestClient restClient = RestClient.create();
  private final ObjectMapper objectMapper = new ObjectMapper();

  public List<PriceItem> fetchPrices(Collection<Long> appIds) {
    List<Map<String, Object>> ids = appIds.stream().<Map<String, Object>>map(id -> Map.of("appid", id)).toList();
    Map<String, Object> input = Map.of(
        "ids", ids,
        "context", Map.of("language", "koreana", "country_code", "KR"),
        "data_request", Map.of());
    URI uri = UriComponentsBuilder.fromUriString(GET_ITEMS_URL)
        .queryParam("input_json", URLEncoder.encode(toJson(input), StandardCharsets.UTF_8))
        .build(true)
        .toUri();
    Map<String, Object> body = restClient.get().uri(uri).retrieve().body(new ParameterizedTypeReference<>() {});
    return parse(body);
  }

  // 응답의 response.store_items를 꺼내 가격을 알 수 있는 항목만 돌려준다.
  // type이 0(게임)이 아닌 항목(DLC=4 등), success가 1이 아닌 항목, 판매 정보(best_purchase_option)가 없는 항목
  // (출시 전, 판매 중지 등)은 기존 값을 유지하도록 뺀다.
  static List<PriceItem> parse(Map<String, Object> body) {
    List<PriceItem> items = new ArrayList<>();
    if (body == null || !(body.get("response") instanceof Map<?, ?> response)
        || !(response.get("store_items") instanceof List<?> storeItems)) {
      return items;
    }
    for (Object raw : storeItems) {
      if (!(raw instanceof Map<?, ?> item) || !(item.get("appid") instanceof Number appId)
          || !(item.get("success") instanceof Number success) || success.intValue() != 1
          || !(item.get("type") instanceof Number type) || type.intValue() != GAME_TYPE) {
        continue;
      }
      if (Boolean.TRUE.equals(item.get("is_free"))) {
        items.add(new PriceItem(appId.longValue(), "무료", 0));
        continue;
      }
      if (item.get("best_purchase_option") instanceof Map<?, ?> option
          && option.get("formatted_final_price") instanceof String price) {
        int discount = option.get("discount_pct") instanceof Number n ? n.intValue() : 0;
        items.add(new PriceItem(appId.longValue(), price, discount));
      }
    }
    return items;
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException("GetItems 요청 JSON 생성 실패", e);
    }
  }

  // priceInfo는 스팀이 내려주는 표시용 문자열(예: "₩ 19,800"), 무료 게임은 "무료". 할인이 없으면 discountPercent는 0.
  public record PriceItem(long appId, String priceInfo, int discountPercent) {
  }
}
