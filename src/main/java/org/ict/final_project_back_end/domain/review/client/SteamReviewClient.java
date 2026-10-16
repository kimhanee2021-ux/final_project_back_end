package org.ict.final_project_back_end.domain.review.client;

import org.ict.final_project_back_end.domain.review.exception.SteamUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.DefaultUriBuilderFactory;

import java.util.HashMap;
import java.util.Map;

@Component
public class SteamReviewClient {

    private final RestClient rest;

    public SteamReviewClient(
            @Value("${steam.store.base-url:https://store.steampowered.com}") String baseUrl,
            @Value("${steam.store.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${steam.store.read-timeout-ms:5000}") int readTimeoutMs) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);

        // 변수 값을 엄격하게 인코딩한다. cursor에 들어 있는 + / = 같은 문자 때문에 같은 페이지가 반복되는 문제 방지.
        DefaultUriBuilderFactory uriFactory = new DefaultUriBuilderFactory(baseUrl);
        uriFactory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.VALUES_ONLY);

        this.rest = RestClient.builder()
                .uriBuilderFactory(uriFactory)
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.USER_AGENT, "final-project-back-end/1.0")
                .build();
    }

    /**
     * @param filter   recent(최신순) | all(도움됨순)
     * @param language all | korean | english ...
     * @param reviewType all | positive | negative
     * @param numPerPage 0이면 요약만 필요한 호출
     * @param dayRange null이면 전체 기간. 값이 있으면 최근 N일(filter=all일 때 유효)
     */
    public SteamReviewResponse fetch(long appId, String filter, String language, String reviewType,
                                     String cursor, int numPerPage, Integer dayRange) {
        try {
            return call(appId, filter, language, reviewType, cursor, numPerPage, dayRange);
        } catch (HttpServerErrorException | HttpClientErrorException.TooManyRequests e) {
            // 429 / 5xx는 한 번만 짧게 재시도
            sleepQuietly(1000);
            try {
                return call(appId, filter, language, reviewType, cursor, numPerPage, dayRange);
            } catch (RestClientException retryEx) {
                throw new SteamUnavailableException(appId, retryEx);
            }
        } catch (RestClientException e) {
            throw new SteamUnavailableException(appId, e);
        }
    }

    private SteamReviewResponse call(long appId, String filter, String language, String reviewType,
                                     String cursor, int numPerPage, Integer dayRange) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("appId", appId);
        vars.put("filter", filter);
        vars.put("language", language);
        vars.put("reviewType", reviewType);
        vars.put("size", numPerPage);
        vars.put("cursor", cursor);

        String template = "/appreviews/{appId}?json=1&filter={filter}&language={language}"
                + "&review_type={reviewType}&purchase_type=all&num_per_page={size}&cursor={cursor}";
        if (dayRange != null) {
            template += "&day_range={dayRange}";
            vars.put("dayRange", dayRange);
        }

        return rest.get().uri(template, vars).retrieve().body(SteamReviewResponse.class);
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
