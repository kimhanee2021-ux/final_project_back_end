package org.ict.final_project_back_end.domain.recommendation.client;

import com.sun.net.httpserver.HttpServer;
import org.ict.final_project_back_end.domain.recommendation.client.dto.ModelRequest;
import org.ict.final_project_back_end.domain.recommendation.client.dto.ModelResult;
import org.ict.final_project_back_end.domain.recommendation.exception.NoPlayedGamesException;
import org.ict.final_project_back_end.domain.recommendation.exception.RecommendationServerException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

// 실제 모델 서버 대신 가짜 HTTP 서버로 응답 형식({code, error})에 따른 분기를 검증한다
class FastApiRecommendationClientTest {

  private static final ModelRequest REQUEST = new ModelRequest(List.of(), List.of(), 10, 0);

  private HttpServer server;
  private FastApiRecommendationClient client;

  @BeforeEach
  void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.start();
    client = new FastApiRecommendationClient("http://localhost:" + server.getAddress().getPort(), 5);
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  @Test
  void mapsSnakeCaseResponse() {
    respond(200, "{\"items\":[{\"app_id\":11,\"score\":0.9,\"source\":\"main\"}],"
        + "\"used_app_ids\":[1],\"ignored_app_ids\":[2]}");

    ModelResult result = client.recommend(REQUEST);

    assertEquals(11L, result.items().get(0).appId());
    assertEquals(List.of(1L), result.usedAppIds());
    assertEquals(List.of(2L), result.ignoredAppIds());
  }

  @Test
  void translatesNoKnownGamesCodeToNoPlayedGames() {
    respond(422, "{\"code\":\"NO_KNOWN_GAMES\",\"error\":\"없음\"}");

    assertThrows(NoPlayedGamesException.class, () -> client.recommend(REQUEST));
  }

  @Test
  void treatsOtherModelErrorsAsServerErrors() {
    respond(400, "{\"code\":\"VALIDATION_ERROR\",\"error\":\"played_games: too long\"}");

    assertThrows(RecommendationServerException.class, () -> client.recommend(REQUEST));
  }

  @Test
  void treatsNonJsonErrorBodyAsServerError() {
    respond(502, "<html>Bad Gateway</html>");

    assertThrows(RecommendationServerException.class, () -> client.recommend(REQUEST));
  }

  private void respond(int status, String body) {
    server.createContext("/internal/v1/recommendations", exchange -> {
      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(status, bytes.length);
      exchange.getResponseBody().write(bytes);
      exchange.close();
    });
  }
}
