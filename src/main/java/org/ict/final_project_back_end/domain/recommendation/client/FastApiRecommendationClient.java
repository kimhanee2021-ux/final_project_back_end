package org.ict.final_project_back_end.domain.recommendation.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.ict.final_project_back_end.domain.recommendation.client.dto.ModelError;
import org.ict.final_project_back_end.domain.recommendation.client.dto.ModelRequest;
import org.ict.final_project_back_end.domain.recommendation.client.dto.ModelResult;
import org.ict.final_project_back_end.domain.recommendation.exception.NoPlayedGamesException;
import org.ict.final_project_back_end.domain.recommendation.exception.RecommendationServerException;
import org.ict.final_project_back_end.global.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.time.Duration;

// FastAPI 추천 모델 서버(POST /internal/v1/recommendations) 호출만 담당한다. 모델 서버는 내부망에서만 연다.
// 모델 서버는 오류를 { code, error }로 내리므로 HTTP 상태가 아니라 code로 분기한다.
@Slf4j
@Component
public class FastApiRecommendationClient implements RecommendationModelClient {

  private final RestClient restClient;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public FastApiRecommendationClient(
      @Value("${app.recommend.base-url}") String baseUrl,
      @Value("${app.recommend.timeout-seconds}") long timeoutSeconds) {
    // 모델 서버(uvicorn)는 HTTP/1.1만 쓴다. JDK 기본값(HTTP/2 업그레이드 시도)이면 POST 본문이 유실된다.
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
        HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build());
    factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
    this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
  }

  @Override
  public ModelResult recommend(ModelRequest request) {
    try {
      ModelResult result = restClient.post()
          .uri("/internal/v1/recommendations")
          .body(request)
          .retrieve()
          .onStatus(HttpStatusCode::isError, (req, res) -> {
            throw toException(res.getStatusCode().value(), res.getBody());
          })
          .body(ModelResult.class);
      if (result == null || result.items() == null) {
        throw new RecommendationServerException();
      }
      return result;
    } catch (RestClientException e) {
      log.warn("추천 모델 서버 호출 실패: {}", e.getClass().getSimpleName());
      throw new RecommendationServerException();
    }
  }

  // NO_KNOWN_GAMES만 사용자에게 안내할 상태이고, 나머지(400 요청 계약 위반, 500 서버 오류 등)는 서버 문제로 처리한다.
  private BusinessException toException(int status, InputStream body) {
    ModelError error = readError(body);
    if (error != null && error.isNoKnownGames()) {
      return new NoPlayedGamesException();
    }
    log.warn("추천 모델 서버 오류 응답: HTTP {}, code={}, error={}",
        status, error == null ? null : error.code(), error == null ? null : error.error());
    return new RecommendationServerException();
  }

  // 본문이 { code, error } 형식이 아니면(프록시 오류 페이지 등) null
  private ModelError readError(InputStream body) {
    try {
      return objectMapper.readValue(body, ModelError.class);
    } catch (IOException e) {
      return null;
    }
  }
}
