package org.ict.final_project_back_end.domain.purchase.service;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.ict.final_project_back_end.global.constant.SteamUrls.STEAM_STORE_BASE_URL;

// 구매내역의 상품 이름을 스팀 appId로 찾고, 그 상품이 DLC면 본편이 무엇인지 확인한다.
// 실패하면 null을 돌려준다(이름만으로 억지로 묶지 않기 위해).
@Component
public class SteamAppInfoClient {

  public record AppInfo(Long appId, String type, Long fullGameAppId) {
    public boolean isDlcOf(Long baseAppId) {
      return "dlc".equals(type) && baseAppId != null && baseAppId.equals(fullGameAppId);
    }
  }

  private final RestClient restClient = RestClient.create();

  // 상점 검색 결과 중 이름이 정확히 같은(정규화 기준) 상품의 appId
  public Long searchExactAppId(String name) {
    URI uri = UriComponentsBuilder.fromUriString(STEAM_STORE_BASE_URL + "/api/storesearch/")
        .queryParam("term", name).queryParam("cc", "kr").queryParam("l", "english")
        .encode().build().toUri();
    try {
      Map<String, Object> body = restClient.get().uri(uri).retrieve().body(new ParameterizedTypeReference<>() {});
      if (body == null || !(body.get("items") instanceof List<?> items)) {
        return null;
      }
      String wanted = GameNameMatcher.normalize(name);
      for (Object item : items) {
        if (item instanceof Map<?, ?> map && wanted.equals(GameNameMatcher.normalize(String.valueOf(map.get("name"))))
            && map.get("id") instanceof Number id) {
          return id.longValue();
        }
      }
      return null;
    } catch (RestClientException e) {
      return null;
    }
  }

  // 상품 종류(game/dlc 등)와 DLC의 본편 appId
  public AppInfo fetchInfo(Long appId) {
    URI uri = UriComponentsBuilder.fromUriString(STEAM_STORE_BASE_URL + "/api/appdetails")
        .queryParam("appids", appId).queryParam("filters", "basic").build(true).toUri();
    try {
      Map<String, Object> body = restClient.get().uri(uri).retrieve().body(new ParameterizedTypeReference<>() {});
      if (body == null || !(body.get(String.valueOf(appId)) instanceof Map<?, ?> app)
          || !(app.get("data") instanceof Map<?, ?> data)) {
        return null;
      }
      Long fullGame = null;
      if (data.get("fullgame") instanceof Map<?, ?> full && full.get("appid") != null) {
        fullGame = Long.valueOf(String.valueOf(full.get("appid")));
      }
      return new AppInfo(appId, String.valueOf(data.get("type")), fullGame);
    } catch (RestClientException | NumberFormatException e) {
      return null;
    }
  }
}
