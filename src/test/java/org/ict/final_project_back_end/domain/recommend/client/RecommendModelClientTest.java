package org.ict.final_project_back_end.domain.recommend.client;

import org.ict.final_project_back_end.domain.recommend.exception.RecommendException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

// 실제 추천 서버 없이 요청 본문 모양(snake_case)과 응답·오류 해석만 검증한다
class RecommendModelClientTest {

  private MockRestServiceServer server;
  private RecommendModelClient client;

  private final RecommendModelClient.Request request = new RecommendModelClient.Request(
      List.of(new RecommendModelClient.PlayedGame(730, 1.5)), List.of(730L, 570L), 5, 2);

  @BeforeEach
  void setup() {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://model");
    server = MockRestServiceServer.bindTo(builder).build();
    client = new RecommendModelClient(builder);
  }

  @Test
  void sendsSnakeCaseBodyAndReadsItems() {
    server.expect(requestTo("http://model/internal/v1/recommendations"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(jsonPath("$.played_games[0].app_id").value(730))
        .andExpect(jsonPath("$.played_games[0].playtime_hours").value(1.5))
        .andExpect(jsonPath("$.exclude_app_ids[1]").value(570))
        .andExpect(jsonPath("$.limit").value(5))
        .andExpect(jsonPath("$.new_slots").value(2))
        .andRespond(withSuccess("""
            {"items":[{"app_id":374320,"score":0.91,"source":"main"},{"app_id":5349560,"score":0.4,"source":"new"}],
             "used_app_ids":[730],"ignored_app_ids":[]}
            """, MediaType.APPLICATION_JSON));

    RecommendModelClient.Response response = client.recommend(request);

    assertEquals(2, response.items().size());
    assertEquals(374320L, response.items().getFirst().appId());
    assertEquals("new", response.items().get(1).source());
    assertEquals(List.of(730L), response.usedAppIds());
    server.verify();
  }

  @Test
  void unknownGamesMeansNoPlayedGames() {
    server.expect(requestTo("http://model/internal/v1/recommendations"))
        .andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT).contentType(MediaType.APPLICATION_JSON)
            .body("{\"code\":\"NO_KNOWN_GAMES\",\"error\":\"...\"}"));

    RecommendException e = assertThrows(RecommendException.class, () -> client.recommend(request));
    assertEquals("NO_PLAYED_GAMES", e.getCode());
  }

  @Test
  void serverErrorAndBrokenBodyAreInvalidResponse() {
    server.expect(requestTo("http://model/internal/v1/recommendations")).andRespond(withServerError());
    assertEquals("RECOMMEND_FAILED", assertThrows(RecommendException.class, () -> client.recommend(request)).getCode());

    server.reset();
    server.expect(requestTo("http://model/internal/v1/recommendations"))
        .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
    assertEquals("RECOMMEND_FAILED", assertThrows(RecommendException.class, () -> client.recommend(request)).getCode());
  }

  @Test
  void connectionFailureMeansUnavailable() {
    server.expect(requestTo("http://model/internal/v1/recommendations"))
        .andRespond(req -> {
          throw new ResourceAccessException("Connection refused");
        });

    RecommendException e = assertThrows(RecommendException.class, () -> client.recommend(request));
    assertEquals("RECOMMEND_UNAVAILABLE", e.getCode());
  }
}
