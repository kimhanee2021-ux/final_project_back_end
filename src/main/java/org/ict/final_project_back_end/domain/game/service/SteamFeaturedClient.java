package org.ict.final_project_back_end.domain.game.service;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.ict.final_project_back_end.global.constant.SteamUrls.STEAM_STORE_BASE_URL;

// 스팀 상점 홈 추천(/api/featured)과 카테고리별 목록(/api/featuredcategories) API 호출만 담당한다.
// 두 응답 모두 키에 밑줄이 섞여 있고(featuredcategories는 카테고리 키 자체가 가변적) 레코드로 바로 매핑할 수 없어
// Map으로 받아 호출하는 쪽(FeaturedGameSyncService)에서 직접 파싱한다. 실패하면 RestClientException을 그대로 던진다.
@Component
public class SteamFeaturedClient {

  private final RestClient restClient = RestClient.create();

  public Map<String, Object> fetchFeatured() {
    return fetch(STEAM_STORE_BASE_URL + "/api/featured");
  }

  public Map<String, Object> fetchFeaturedCategories() {
    return fetch(STEAM_STORE_BASE_URL + "/api/featuredcategories");
  }

  private Map<String, Object> fetch(String baseUrl) {
    URI uri = UriComponentsBuilder.fromUriString(baseUrl)
        .queryParam("cc", "kr")
        .queryParam("l", "korean")
        .build(true)
        .toUri();
    Map<String, Object> body = restClient.get().uri(uri).retrieve().body(new ParameterizedTypeReference<>() {});
    return body == null ? Map.of() : body;
  }

  // 아이템 하나(appid/id, name, discounted, discount_percent, original_price, final_price, currency, header_image)를
  // 공통 형태로 꺼낸다. featured의 아이템은 "id", featuredcategories의 아이템은 "id"를 그대로 쓴다.
  public static Item toItem(Object raw) {
    if (!(raw instanceof Map<?, ?> m)) {
      return null;
    }
    Object appId = m.get("id");
    if (!(appId instanceof Number)) {
      return null;
    }
    return new Item(
        ((Number) appId).longValue(),
        asString(m.get("name")),
        Boolean.TRUE.equals(m.get("discounted")),
        toInt(m.get("discount_percent")),
        toNullableInt(m.get("original_price")),
        toNullableInt(m.get("final_price")),
        asString(m.get("currency")),
        asString(m.get("header_image")));
  }

  @SuppressWarnings("unchecked")
  public static List<Object> asItemList(Object raw) {
    return raw instanceof List<?> list ? (List<Object>) list : List.of();
  }

  private static String asString(Object value) {
    return value == null ? null : value.toString();
  }

  private static int toInt(Object value) {
    return value instanceof Number n ? n.intValue() : 0;
  }

  private static Integer toNullableInt(Object value) {
    return value instanceof Number n ? n.intValue() : null;
  }

  public record Item(long appId, String name, boolean discounted, int discountPercent,
                      Integer originalPrice, Integer finalPrice, String currency, String headerImage) {
  }
}
