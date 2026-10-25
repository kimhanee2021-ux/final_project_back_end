package org.ict.final_project_back_end.domain.wishsales.client;

import org.ict.final_project_back_end.domain.wishsales.dto.PricePoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

// [추가] ITAD(IsThereAnyDeal) API 호출 클래스.
// 흐름: 스팀 appid -> ITAD 게임 ID(lookup) -> 스팀 가격 이력(history).
// API 키는 환경변수 ITAD_API_KEY에서 읽는다. 코드에 키를 직접 넣지 않는다.
@Component
public class ItadClient {
    private static final String BASE = "https://api.isthereanydeal.com";
    // 조회 시작일. 실제 기록은 2024-12-07부터 있어서 그보다 이전 날짜를 넣어도 그 날짜부터 받는다.
    private static final String SINCE = "2024-10-19T00:00:00Z";
    // 이력에는 여러 상점이 섞여 있어서, 스팀 기록만 쓰기 위해 상점 이름으로 거른다
    private static final String STEAM_SHOP_NAME = "Steam";

    // RestClient: Spring의 동기 HTTP 클라이언트. 호출할 때마다 새로 만들지 않도록 필드로 둔다.
    private final RestClient http = RestClient.create();
    private final String apiKey;

    // @Value("${ITAD_API_KEY:}"): 환경변수가 없으면 빈 문자열이 들어온다.
    public ItadClient(@Value("${ITAD_API_KEY:}") String apiKey) {
        this.apiKey = apiKey;
    }

    // 키가 설정되어 있는지 확인. 서비스가 키 없이 호출하지 않도록 먼저 검사한다.
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    // 스팀 appid로 ITAD 게임 ID를 찾는다.
    // 응답 JSON 구조가 예상과 다를 수 있어서 game.id와 id 두 곳을 모두 확인한다. (실제 응답으로 확인 필요)
    public Optional<String> lookupId(long appId) {
        JsonNode root = http.get()
                .uri(BASE + "/games/lookup/v1?key={k}&appid={a}", apiKey, appId)
                .retrieve()
                .body(JsonNode.class);
        if (root == null) return Optional.empty();
        JsonNode id = root.path("game").path("id");   // 1순위: game 객체 안의 id
        if (id.isMissingNode()) id = root.path("id"); // 2순위: 최상위 id
        return id.isTextual() ? Optional.of(id.asText()) : Optional.empty();
    }

    // ITAD 게임 ID로 가격 이력을 받아서 스팀 기록만 시간순으로 돌려준다.
    // country=KR: 원화 가격 기준으로 받기 위해 지정
    public List<PricePoint> history(String itadId) {
        JsonNode root = http.get()
                .uri(BASE + "/games/history/v2?key={k}&id={i}&country=KR&since={s}", apiKey, itadId, SINCE)
                .retrieve()
                .body(JsonNode.class);
        List<PricePoint> out = new ArrayList<>();
        // 응답이 배열이 아니면 읽을 수 없으므로 빈 목록을 돌려준다
        if (root == null || !root.isArray()) return out;
        for (JsonNode item : root) {
            // 스팀이 아닌 상점 기록은 건너뛴다
            if (!STEAM_SHOP_NAME.equals(item.path("shop").path("name").asText())) continue;
            String ts = item.path("timestamp").asText("");
            if (ts.isEmpty()) continue;
            // deal.cut: 해당 시점의 할인율. 값이 없으면 0으로 본다
            int cut = item.path("deal").path("cut").asInt(0);
            out.add(new PricePoint(OffsetDateTime.parse(ts), cut));
        }
        // 응답 순서를 믿지 않고 시각 기준으로 다시 정렬한다 (세일 횟수 계산은 순서가 중요함)
        out.sort(Comparator.comparing(PricePoint::at));
        return out;
    }
}
