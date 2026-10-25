package org.ict.final_project_back_end.domain.recommend.client;

import lombok.extern.slf4j.Slf4j;
import org.ict.final_project_back_end.domain.recommend.exception.RecommendException;
import org.ict.final_project_back_end.global.config.FastApiClientConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

// FastAPI 추천 서버(POST /internal/v1/recommendations) 호출.
@Slf4j
@Component
public class FastApiRecommendModelClient implements RecommendModelClient {

  private static final String PATH = "/internal/v1/recommendations";

  private final RestClient http;

  public FastApiRecommendModelClient(@Qualifier(FastApiClientConfig.FAST_API_REST_CLIENT) RestClient http) {
    this.http = http;
  }

  // 모델이 아는 게임이 이력에 하나도 없으면 추천 서버가 422(NO_KNOWN_GAMES)를 준다 -> noPlayedGames
  @Override
  public Response recommend(Request request) {
    Response response;
    try {
      response = http.post()
          .uri(PATH)
          .contentType(MediaType.APPLICATION_JSON)
          .body(request)
          .retrieve()
          .onStatus(status -> status.value() == 422, (req, res) -> {
            throw RecommendException.noPlayedGames();
          })
          .onStatus(HttpStatusCode::isError, (req, res) -> {
            log.warn("추천 서버 오류 응답 HTTP {}", res.getStatusCode().value());
            throw RecommendException.invalidResponse();
          })
          .body(Response.class);
    } catch (ResourceAccessException e) {
      log.warn("추천 서버 연결 실패: {}", e.getMessage());
      throw RecommendException.unavailable();
    } catch (RestClientException e) {
      log.warn("추천 서버 응답 해석 실패: {}", e.getMessage());
      throw RecommendException.invalidResponse();
    }
    if (response == null || response.items() == null) {
      throw RecommendException.invalidResponse();
    }
    return response;
  }
}
