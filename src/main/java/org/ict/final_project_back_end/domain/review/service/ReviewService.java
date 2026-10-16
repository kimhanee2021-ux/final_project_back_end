package org.ict.final_project_back_end.domain.review.service;

import org.ict.final_project_back_end.domain.review.client.SteamReviewClient;
import org.ict.final_project_back_end.domain.review.client.SteamReviewResponse;
import org.ict.final_project_back_end.domain.review.client.SteamReviewResponse.QuerySummary;
import org.ict.final_project_back_end.domain.review.client.SteamReviewResponse.SteamReview;
import org.ict.final_project_back_end.domain.review.dto.ReviewDtos.Item;
import org.ict.final_project_back_end.domain.review.dto.ReviewDtos.Page;
import org.ict.final_project_back_end.domain.review.dto.ReviewDtos.Sort;
import org.ict.final_project_back_end.domain.review.dto.ReviewDtos.Summary;
import org.ict.final_project_back_end.domain.review.dto.ReviewDtos.Type;
import org.ict.final_project_back_end.domain.review.exception.SteamUnavailableException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;

@Service
public class ReviewService {

    private static final int MIN_REVIEWS = 10;
    private static final int MAX_CONTENT_LENGTH = 2000;

    private static final Map<String, String> LABELS = Map.of(
            "Overwhelmingly Positive", "압도적으로 긍정적",
            "Very Positive", "매우 긍정적",
            "Positive", "긍정적",
            "Mostly Positive", "대체로 긍정적",
            "Mixed", "복합적",
            "Mostly Negative", "대체로 부정적",
            "Negative", "부정적",
            "Very Negative", "매우 부정적",
            "Overwhelmingly Negative", "압도적으로 부정적",
            "No user reviews", "리뷰 없음");

    private final SteamReviewClient client;

    public ReviewService(SteamReviewClient client) {
        this.client = client;
    }

    /** 요약: 6시간 캐시. 예외는 캐시되지 않는다. sync=true로 같은 게임의 동시 요청을 스팀 호출 1번으로 합친다. */
    @Cacheable(cacheManager = "reviewCacheManager", cacheNames = "reviewSummary", key = "#appId", sync = true)
    public Summary getSummary(long appId) {
        QuerySummary all = requireSummary(appId, client.fetch(appId, "all", "all", "all", "*", 0, null));
        QuerySummary recent = requireSummary(appId, client.fetch(appId, "all", "all", "all", "*", 0, 30));

        int pos = all.totalPositive();
        int neg = all.totalNegative();
        int total = pos + neg;
        double ratio = total == 0 ? 0 : (double) pos / total;
        double stars = Math.round(ratio * 5 * 10) / 10.0;

        int rp = recent.totalPositive();
        int rn = recent.totalNegative();
        Double recentRatio = (rp + rn) == 0 ? null : (double) rp / (rp + rn);

        String steam = all.reviewScoreDesc();
        String label = steam == null ? "리뷰 없음" : LABELS.getOrDefault(steam, steam);

        return new Summary(appId, label, steam, pos, neg, total, ratio, stars,
                total >= MIN_REVIEWS, rp, rn, recentRatio);
    }

    /** 리뷰 목록: 10분 캐시. 파라미터 조합이 캐시 키. */
    @Cacheable(cacheManager = "reviewCacheManager", cacheNames = "reviewPage", sync = true,
            key = "#appId + ':' + #sort.name() + ':' + #type.name() + ':' + #language + ':' + #cursor + ':' + #size")
    public Page getReviews(long appId, Sort sort, Type type, String language, String cursor, int size) {
        String filter = sort == Sort.RECENT ? "recent" : "all";
        SteamReviewResponse res = client.fetch(appId, filter, language,
                type.name().toLowerCase(Locale.ROOT), cursor, size, null);

        if (res == null || res.reviews() == null) {
            throw new SteamUnavailableException(appId, null);
        }

        var items = res.reviews().stream().map(this::toItem).toList();
        String next = res.cursor();
        boolean hasMore = !items.isEmpty() && next != null && !next.equals(cursor);
        return new Page(items, hasMore ? next : null, hasMore);
    }

    private Item toItem(SteamReview r) {
        int minutes = r.author() == null ? 0 : r.author().playtimeForever();
        String content = r.review() == null ? "" : r.review();
        if (content.length() > MAX_CONTENT_LENGTH) {
            content = content.substring(0, MAX_CONTENT_LENGTH);
        }
        return new Item(r.recommendationid(), r.votedUp(), minutes / 60, r.language(),
                content, Instant.ofEpochSecond(r.timestampCreated()), r.votesUp());
    }

    private QuerySummary requireSummary(long appId, SteamReviewResponse res) {
        if (res == null || res.success() != 1 || res.querySummary() == null) {
            throw new SteamUnavailableException(appId, null);
        }
        return res.querySummary();
    }
}
