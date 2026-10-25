package org.ict.final_project_back_end.domain.aisearch.client;

import lombok.extern.slf4j.Slf4j;
import org.ict.final_project_back_end.domain.aisearch.exception.AiSearchException;
import org.ict.final_project_back_end.global.config.FastApiClientConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

// FastAPI 검색 모델(POST /internal/v1/search) 호출.
@Slf4j
@Component
public class FastApiGameSearchModelClient implements GameSearchModelClient {

  private static final String PATH = "/internal/v1/search";

  private final RestClient http;

  public FastApiGameSearchModelClient(@Qualifier(FastApiClientConfig.FAST_API_REST_CLIENT) RestClient http) {
    this.http = http;
  }

  // 쓸 수 있는 키워드가 없으면 검색 모델이 422(NO_SEARCH_KEYWORDS)를 준다 -> notUnderstood
  @Override
  public Response search(SearchKeywords keywords, int limit) {
    Response response;
    try {
      response = http.post()
          .uri(PATH)
          .contentType(MediaType.APPLICATION_JSON)
          .body(new Request(keywords.tags(), keywords.excludeTags(), keywords.description(), keywords.similarTo(), limit))
          .retrieve()
          .onStatus(status -> status.value() == 422, (req, res) -> {
            throw AiSearchException.notUnderstood();
          })
          .onStatus(HttpStatusCode::isError, (req, res) -> {
            log.warn("검색 모델 오류 응답 HTTP {}", res.getStatusCode().value());
            throw AiSearchException.unavailable();
          })
          .body(Response.class);
    } catch (ResourceAccessException e) {
      log.warn("검색 모델 연결 실패: {}", e.getMessage());
      throw AiSearchException.unavailable();
    } catch (RestClientException e) {
      log.warn("검색 모델 응답 해석 실패: {}", e.getMessage());
      throw AiSearchException.unavailable();
    }
    if (response == null || response.items() == null) {
      throw AiSearchException.unavailable();
    }
    return response;
  }
}
