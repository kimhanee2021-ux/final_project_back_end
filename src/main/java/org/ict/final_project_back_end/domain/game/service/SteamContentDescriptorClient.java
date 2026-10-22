package org.ict.final_project_back_end.domain.game.service;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.ict.final_project_back_end.global.constant.SteamUrls.STEAM_STORE_BASE_URL;

// 스팀 상점 appdetails API에서 게임의 콘텐츠 설명자(content_descriptors.ids)만 꺼내 온다.
// content_descriptors는 filters를 붙이면 내려오지 않아 appId 한 개씩만 물을 수 있다.
// 실패하면 RestClientException을 그대로 던지고, 재시도/건너뛰기 정책은 호출하는 쪽이 정한다.
@Component
public class SteamContentDescriptorClient {

  private final RestClient restClient = RestClient.create();

  // 설명자가 없는 게임은 빈 Set. 스팀이 앱 정보를 주지 않아 알 수 없으면 null.
  public Set<Integer> fetchDescriptorIds(long appId) {
    URI uri = UriComponentsBuilder.fromUriString(STEAM_STORE_BASE_URL + "/api/appdetails")
        .queryParam("cc", "kr")
        .queryParam("appids", appId)
        .build(true)
        .toUri();
    Map<String, AppDetails> body = restClient.get().uri(uri).retrieve()
        .body(new ParameterizedTypeReference<>() {});
    AppDetails app = body == null ? null : body.get(String.valueOf(appId));
    if (app == null || !app.success() || !(app.data() instanceof Map<?, ?> data)) {
      return null;
    }
    Set<Integer> ids = new HashSet<>();
    if (data.get("content_descriptors") instanceof Map<?, ?> descriptors
        && descriptors.get("ids") instanceof Iterable<?> rawIds) {
      for (Object id : rawIds) {
        if (id instanceof Number n) {
          ids.add(n.intValue());
        }
      }
    }
    return ids;
  }

  // data는 성공 시 객체, 정보가 없으면 빈 배열([])로 와서 Object로 받아 직접 꺼낸다
  record AppDetails(boolean success, Object data) {
  }
}
