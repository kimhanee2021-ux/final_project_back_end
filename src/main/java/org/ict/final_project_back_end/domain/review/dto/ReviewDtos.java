package org.ict.final_project_back_end.domain.review.dto;

import java.time.Instant;
import java.util.List;

/** 프론트·안드로이드에 내려주는 응답 형태. */
public final class ReviewDtos {

    private ReviewDtos() {
    }

    public enum Sort { RECENT, HELPFUL }

    public enum Type { ALL, POSITIVE, NEGATIVE }

    /**
     * @param label            한국어 등급 (예: 매우 긍정적)
     * @param steamLabel       스팀 원본 등급 문자열
     * @param positiveRatio    0.0 ~ 1.0
     * @param starRating       긍정 비율 × 5 (소수 첫째 자리). 스팀 별점이 아니라 환산값
     * @param enoughReviews    리뷰 10개 이상인지. false면 화면에서 "리뷰 부족" 처리 권장
     * @param recentPositiveRatio 최근 30일 리뷰가 없으면 null
     */
    public record Summary(
            long appId,
            String label,
            String steamLabel,
            int totalPositive,
            int totalNegative,
            int totalReviews,
            double positiveRatio,
            double starRating,
            boolean enoughReviews,
            int recentPositive,
            int recentNegative,
            Double recentPositiveRatio) {
    }

    public record Item(
            String id,
            boolean recommended,
            int playtimeHours,
            String language,
            String content,
            Instant createdAt,
            int helpfulCount) {
    }

    public record Page(List<Item> items, String nextCursor, boolean hasMore) {
    }
}
