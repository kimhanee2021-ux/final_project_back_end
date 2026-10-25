package org.ict.final_project_back_end.domain.aisearch.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.ict.final_project_back_end.domain.aisearch.exception.AiSearchException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

// FastAPI 검색 모델(POST /internal/v1/search) 호출만 맡는다. 추천과 같은 내부 서버라 주소 설정(app.recommend.base-url)을 같이 쓴다.
@Slf4j
@Component
public class GameSearchModelClient {

  private static final String PATH = "/internal/v1/search";

  private final RestClient http;

  @Autowired
  public GameSearchModelClient(@Value("${app.recommend.base-url:http://localhost:8000}") String baseUrl,
                               @Value("${app.recommend.timeout-seconds:30}") long timeoutSeconds) {
    this(RestClient.builder().requestFactory(requestFactory(timeoutSeconds)).baseUrl(baseUrl));
  }

  // 테스트에서 가짜 서버(MockRestServiceServer)를 붙인 builder를 넘긴다
  GameSearchModelClient(RestClient.Builder builder) {
    this.http = builder.build();
  }

  private static JdkClientHttpRequestFactory requestFactory(long timeoutSeconds) {
    // JDK 기본값(HTTP/2)은 http://에서 h2c 업그레이드를 시도하는데, uvicorn이 이를 거부해 본문 없는 요청(400)이 된다
    HttpClient client = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(3))
        .build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
    factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
    return factory;
  }

  // 쓸 수 있는 키워드가 없으면 검색 모델이 422(NO_SEARCH_KEYWORDS)를 준다 -> notUnderstood
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

  // 검색 모델 요청/응답 본문 (FastAPI 쪽 이름이 snake_case라 JSON 이름을 따로 지정한다)

  public record Request(
      List<String> tags,
      @JsonProperty("exclude_tags") List<String> excludeTags,
      String description,
      @JsonProperty("similar_to") String similarTo,
      int limit) {
  }

  // ignoredTags: 검색 데이터에 없어서 무시된 태그, similarToAppId: similarTo로 찾은 게임 (못 찾았으면 null)
  public record Response(
      List<Item> items,
      @JsonProperty("ignored_tags") List<String> ignoredTags,
      @JsonProperty("similar_to_app_id") Long similarToAppId) {
  }

  // score는 한 번의 검색 안에서 순위를 매기는 값일 뿐 확률이 아니다
  public record Item(@JsonProperty("app_id") long appId, double score) {
  }
}
