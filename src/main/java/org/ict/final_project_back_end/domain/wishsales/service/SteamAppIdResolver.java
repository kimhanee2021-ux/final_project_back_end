package org.ict.final_project_back_end.domain.wishsales.service;

import org.ict.final_project_back_end.domain.wishsales.dto.AppIdResolveDtos.ResolveResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// [추가] 게임 이름으로 스팀 appid를 찾는 서비스.
// 스팀 상점 검색(키 불필요)을 호출하고, 이름과 type이 정확히 같은 기본 게임(type=app)만 고른다.
// DLC, 번들, 사운드트랙은 type이나 이름이 달라서 자연스럽게 제외된다.
// 같은 이름은 24시간 동안 메모리에 저장해서 스팀을 다시 부르지 않는다.
@Service
public class SteamAppIdResolver {
    private static final long TTL_MS = 24L * 60 * 60 * 1000;

    private final RestClient http = RestClient.create();
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    private record Cached(ResolveResponse response, long savedAt) {}

    public ResolveResponse resolve(String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty()) return ResolveResponse.error(name, "게임 이름을 입력해 주세요");

        // 같은 이름을 다시 찾으면 캐시를 쓴다 (찾은 결과와 못 찾은 결과 모두)
        Cached c = cache.get(name.toLowerCase());
        if (c != null && System.currentTimeMillis() - c.savedAt() < TTL_MS) return c.response();

        ResolveResponse result;
        try {
            JsonNode root = http.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https").host("store.steampowered.com")
                            .path("/api/storesearch/")
                            .queryParam("term", name)   // 한글과 공백은 자동으로 인코딩된다
                            .queryParam("l", "koreana")
                            .queryParam("cc", "KR")
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
            Optional<Long> appId = findExactBaseGame(root, name);
            result = appId.map(id -> ResolveResponse.ok(name, id)).orElseGet(() -> ResolveResponse.notFound(name));
        } catch (Exception e) {
            // 네트워크 오류는 캐시하지 않는다 (다음 요청에서 다시 시도)
            return ResolveResponse.error(name, "스팀 검색에 실패했어요");
        }
        cache.put(name.toLowerCase(), new Cached(result, System.currentTimeMillis()));
        return result;
    }

    // 검색 결과 items 중 type=app 이고 이름이 입력과 같은(앞뒤 공백과 대소문자 무시) 첫 항목의 id
    static Optional<Long> findExactBaseGame(JsonNode root, String name) {
        if (root == null) return Optional.empty();
        JsonNode items = root.path("items");
        if (!items.isArray()) return Optional.empty();
        for (JsonNode item : items) {
            if (!"app".equals(item.path("type").asText())) continue;
            String itemName = item.path("name").asText("").trim();
            if (itemName.equalsIgnoreCase(name) && item.path("id").isNumber()) {
                return Optional.of(item.path("id").asLong());
            }
        }
        return Optional.empty();
    }
}
