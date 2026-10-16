package org.ict.final_project_back_end.domain.review.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** 스팀 appreviews 응답 원본. 우리 API 응답(ReviewDtos)과 분리해서 스팀 스펙 변경 영향을 막는다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SteamReviewResponse(
        int success,
        @JsonProperty("query_summary") QuerySummary querySummary,
        List<SteamReview> reviews,
        String cursor) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record QuerySummary(
            @JsonProperty("review_score_desc") String reviewScoreDesc,
            @JsonProperty("total_positive") int totalPositive,
            @JsonProperty("total_negative") int totalNegative) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SteamReview(
            String recommendationid,
            Author author,
            String language,
            String review,
            @JsonProperty("timestamp_created") long timestampCreated,
            @JsonProperty("voted_up") boolean votedUp,
            @JsonProperty("votes_up") int votesUp) {
    }

    /** 작성자의 steamid는 일부러 받지 않는다(식별 정보 차단). playtime_forever는 분 단위. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Author(@JsonProperty("playtime_forever") int playtimeForever) {
    }
}
