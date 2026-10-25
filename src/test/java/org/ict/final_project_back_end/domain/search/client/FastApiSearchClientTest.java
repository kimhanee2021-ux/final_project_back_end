package org.ict.final_project_back_end.domain.search.client;

import com.sun.net.httpserver.HttpServer;
import org.ict.final_project_back_end.domain.search.client.dto.SearchModelRequest;
import org.ict.final_project_back_end.domain.search.client.dto.SearchModelResult;
import org.ict.final_project_back_end.domain.search.exception.SearchException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 진짜 HTTP 서버(JDK 내장)를 가짜 모델 서버로 세워서, 요청 JSON 키와 응답 해석, 오류 처리를 검증한다
class FastApiSearchClientTest {

  private HttpServer server;

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void sendsSnakeCaseKeysAndParsesTheResult() throws Exception {
    AtomicReference<String> path = new AtomicReference<>();
    AtomicReference<String> body = new AtomicReference<>();
    start(200, "{\"items\":[{\"app_id\":413150,\"score\":3.9123}],\"ignored_tags\":[\"Super Hard\"],\"similar_to_app_id\":413150,\"extra\":1}",
        path, body);

    SearchModelResult result = client().search(new SearchModelRequest(List.of("Farming Sim"), List.of("Horror"), "a farming game", "Stardew Valley", 5));

    assertEquals("/internal/v1/search", path.get());
    assertTrue(body.get().contains("\"exclude_tags\":[\"Horror\"]"), body.get());
    assertTrue(body.get().contains("\"similar_to\":\"Stardew Valley\""), body.get());
    assertTrue(body.get().contains("\"limit\":5"), body.get());
    assertEquals(413150L, result.items().get(0).appId());
    assertEquals(3.9123, result.items().get(0).score());
    assertEquals(List.of("Super Hard"), result.ignoredTags());
    assertEquals(413150L, result.similarToAppId());
  }

  @Test
  void similarToAppIdCanBeNull() throws Exception {
    start(200, "{\"items\":[],\"ignored_tags\":[],\"similar_to_app_id\":null}", new AtomicReference<>(), new AtomicReference<>());

    assertNull(client().search(new SearchModelRequest(List.of(), List.of(), "x", "", 5)).similarToAppId());
  }

  @Test
  void anyErrorResponseBecomesASearchServerError() throws Exception {
    start(503, "{\"code\":\"SEARCH_UNAVAILABLE\",\"error\":\"검색 모델이 준비되지 않았습니다\"}", new AtomicReference<>(), new AtomicReference<>());

    SearchException e = assertThrows(SearchException.class, () -> client().search(new SearchModelRequest(List.of(), List.of(), "x", "", 5)));
    assertEquals("SEARCH_SERVER_ERROR", e.getCode());
  }

  @Test
  void aResponseWithoutItemsIsAServerError() throws Exception {
    start(200, "{}", new AtomicReference<>(), new AtomicReference<>());

    assertThrows(SearchException.class, () -> client().search(new SearchModelRequest(List.of(), List.of(), "x", "", 5)));
  }

  @Test
  void anUnreachableServerIsASearchServerError() {
    FastApiSearchClient unreachable = new FastApiSearchClient("http://127.0.0.1:1", 2);

    assertThrows(SearchException.class, () -> unreachable.search(new SearchModelRequest(List.of(), List.of(), "x", "", 5)));
  }

  private FastApiSearchClient client() {
    return new FastApiSearchClient("http://127.0.0.1:" + server.getAddress().getPort(), 5);
  }

  private void start(int status, String responseBody, AtomicReference<String> path, AtomicReference<String> body) throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      path.set(exchange.getRequestURI().getPath());
      body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      byte[] out = responseBody.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(status, out.length);
      exchange.getResponseBody().write(out);
      exchange.close();
    });
    server.start();
  }
}
