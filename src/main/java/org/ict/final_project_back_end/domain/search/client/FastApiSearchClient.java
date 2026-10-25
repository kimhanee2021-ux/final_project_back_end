package org.ict.final_project_back_end.domain.search.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.ict.final_project_back_end.domain.search.client.dto.SearchModelError;
import org.ict.final_project_back_end.domain.search.client.dto.SearchModelRequest;
import org.ict.final_project_back_end.domain.search.client.dto.SearchModelResult;
import org.ict.final_project_back_end.domain.search.exception.SearchException;
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

// FastAPI 검색 모델 서버(POST /internal/v1/search) 호출만 담당한다. 모델 서버는 내부망에서만 연다.
// 모델 서버는 오류를 { code, error }로 내리지만, 검색에서는 사용자에게 구분해 안내할 오류가 없어 모두 서버 오류로 처리한다.
@Slf4j
@Component
public class FastApiSearchClient implements SearchModelClient {

  private final RestClient restClient;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public FastApiSearchClient(
      @Value("${app.search.model-server-url:http://localhost:8000}") String baseUrl,
      @Value("${app.search.timeout-seconds:10}") long timeoutSeconds) {
    // 모델 서버(uvicorn)는 HTTP/1.1만 쓴다. JDK 기본값(HTTP/2 업그레이드 시도)이면 POST 본문이 유실된다.
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
        HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build());
    factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
    this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
  }

  @Override
  public SearchModelResult search(SearchModelRequest request) {
    try {
      SearchModelResult result = restClient.post()
          .uri("/internal/v1/search")
          .body(request)
          .retrieve()
          .onStatus(HttpStatusCode::isError, (req, res) -> {
            logError(res.getStatusCode().value(), res.getBody());
            throw SearchException.serverError();
          })
          .body(SearchModelResult.class);
      if (result == null || result.items() == null) {
        throw SearchException.serverError();
      }
      return result;
    } catch (RestClientException e) {
      log.warn("검색 모델 서버 호출 실패: {}", e.getClass().getSimpleName());
      throw SearchException.serverError();
    }
  }

  private void logError(int status, InputStream body) {
    try {
      SearchModelError error = objectMapper.readValue(body, SearchModelError.class);
      log.warn("검색 모델 서버 오류 응답: HTTP {}, code={}, error={}", status, error.code(), error.error());
    } catch (IOException e) {
      log.warn("검색 모델 서버 오류 응답: HTTP {} (본문을 읽지 못함)", status);
    }
  }
}
