package org.ict.final_project_back_end.domain.wishsales.service;

import org.ict.final_project_back_end.domain.wishsales.client.ItadClient;
import org.ict.final_project_back_end.domain.wishsales.dto.WishlistSalesDtos.PricePoint;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// ITAD 게임 ID 조회 + 가격 이력을 6시간 동안 메모리에 캐시한다.
// 구매 판단(BuyAdviceService)과 할인 달 전망(DiscountOutlookService)이 같은 이력을 함께 쓴다.
@Component
public class PriceHistoryCache {
    private static final long TTL_MS = 6L * 60 * 60 * 1000;

    private final ItadClient itad;
    private final Map<Long, Cached> cache = new ConcurrentHashMap<>();

    private record Cached(List<PricePoint> points, long savedAt) {}

    public PriceHistoryCache(ItadClient itad) {
        this.itad = itad;
    }

    // ITAD 키가 없거나 게임을 찾지 못하면 null. 외부 호출 오류는 그대로 던지고 캐시하지 않는다.
    public List<PricePoint> historyOf(long appId) {
        Cached c = cache.get(appId);
        if (c != null && System.currentTimeMillis() - c.savedAt() < TTL_MS) return c.points();
        if (!itad.isConfigured()) return null;

        Optional<String> itadId = itad.lookupId(appId);
        if (itadId.isEmpty()) return null;

        List<PricePoint> points = itad.history(itadId.get());
        cache.put(appId, new Cached(points, System.currentTimeMillis()));
        return points;
    }
}
