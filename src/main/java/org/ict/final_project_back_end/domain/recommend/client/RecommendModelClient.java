package org.ict.final_project_back_end.domain.recommend.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.ict.final_project_back_end.domain.recommend.exception.RecommendException;
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

// FastAPI 추천 서버(POST /internal/v1/recommendations) 호출만 맡는다.
// 추천 서버는 인증이 없는 내부 서버라 프론트가 아니라 이 클라이언트만 부른다.
// 누구의 어떤 게임을 보낼지는 RecommendationServiceImpl이 정한다.
@Slf4j
@Component
public class RecommendModelClient {

  private static final String PATH = "/internal/v1/recommendations";

  private final RestClient http;

  @Autowired
  public RecommendModelClient(@Value("${app.recommend.base-url:http://localhost:8000}") String baseUrl,
                              @Value("${app.recommend.timeout-seconds:30}") long timeoutSeconds) {
    this(RestClient.builder().requestFactory(requestFactory(timeoutSeconds)).baseUrl(baseUrl));
  }

  // 테스트에서 가짜 서버(MockRestServiceServer)를 붙인 builder를 넘긴다
  RecommendModelClient(RestClient.Builder builder) {
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

  // 모델이 아는 게임이 이력에 하나도 없으면 추천 서버가 422(NO_KNOWN_GAMES)를 준다 -> noPlayedGames
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

  // 추천 서버 요청/응답 본문 (FastAPI 쪽 이름이 snake_case라 JSON 이름을 따로 지정한다)

  // playedGames는 오래된 순 -> 최근 순. 모델은 최근 이력부터 쓴다.
  public record Request(
      @JsonProperty("played_games") List<PlayedGame> playedGames,
      @JsonProperty("exclude_app_ids") List<Long> excludeAppIds,
      int limit,
      @JsonProperty("new_slots") int newSlots) {
  }

  public record PlayedGame(@JsonProperty("app_id") long appId, @JsonProperty("playtime_hours") double playtimeHours) {
  }

  public record Response(
      List<Item> items,
      @JsonProperty("used_app_ids") List<Long> usedAppIds,
      @JsonProperty("ignored_app_ids") List<Long> ignoredAppIds) {
  }

  // score는 한 번의 추천 안에서 순위를 매기는 값일 뿐 확률이 아니다. source: main = 기본, new = 학습에 없던 신작
  public record Item(@JsonProperty("app_id") long appId, double score, String source) {
  }
}
