package org.ict.final_project_back_end.domain.gamenews.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;
import com.sun.net.httpserver.HttpServer;
import org.ict.final_project_back_end.domain.game.service.GameSearchException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GeminiNewsVoiceIntentClientTest {
  @Test void localProviderReceivesHistoryAndCannotInventGameNames() throws Exception {
    var reply = new AtomicReference<>(valid());
    var captured = new AtomicReference<String>();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/v1beta/models/test-model:generateContent", exchange -> {
      captured.set(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
      byte[] bytes = json.writeValueAsBytes(Map.of("candidates", List.of(Map.of("finishReason", "STOP", "content", Map.of("parts", List.of(Map.of("text", reply.get())))))));
      exchange.sendResponseHeaders(200, bytes.length);
      try (var output = exchange.getResponseBody()) { output.write(bytes); }
    });
    server.start();
    try {
      var local = new GeminiNewsVoiceIntentClient("test-only-key", "test-model", "http://127.0.0.1:" + server.getAddress().getPort());
      var context = new org.ict.final_project_back_end.domain.gamenews.dto.NewsVoiceIntent.AiContext(null, null, "PATCH", false, "GAME",
          List.of(new org.ict.final_project_back_end.domain.gamenews.dto.NewsVoiceIntent.Turn("도타2", "CHOOSE_GAME", null, null, "PATCH", false, "GAME")));
      assertEquals("도타2", local.extract("아까 이야기한 게임의 바뀐 내용", context).gameQuery());
      var wireData = json.readTree(json.readTree(captured.get()).path("contents").path(0).path("parts").path(0).path("text").asText());
      assertEquals("도타2", wireData.path("currentContext").path("history").path(0).path("transcript").asText());
      reply.set(valid().replace("도타2", "Invented Game"));
      assertThrows(GameSearchException.class, () -> local.extract("아까 이야기한 게임의 바뀐 내용", context));
    } finally { server.stop(0); }
  }
  @Test void contextPayloadIsBoundedDataAndCannotChangeTheOutputSchema() throws Exception {
    var context = new org.ict.final_project_back_end.domain.gamenews.dto.NewsVoiceIntent.AiContext(570L, "Dota 2", "PATCH", false, null,
        List.of(new org.ict.final_project_back_end.domain.gamenews.dto.NewsVoiceIntent.Turn("도타2 패치 알려줘", "SEARCH", 570L, "Dota 2", "PATCH", false, null)));
    var body = json.readTree(json.writeValueAsBytes(client.requestBody("아니 그거 말고 이벤트", context)));
    var data = json.readTree(body.path("contents").path(0).path("parts").path(0).path("text").asText());
    assertEquals(570L, data.path("currentContext").path("appId").asLong());
    assertEquals("PATCH", data.path("currentContext").path("category").asText());
    assertEquals("도타2 패치 알려줘", data.path("currentContext").path("history").path(0).path("transcript").asText());
    assertEquals(6, body.path("generationConfig").path("responseSchema").path("properties").size());
    assertTrue(body.path("systemInstruction").path("parts").path(0).path("text").asText().contains("정정 내용을 우선"));
  }
  final GeminiNewsVoiceIntentClient client = new GeminiNewsVoiceIntentClient("", "test-model", "https://example.invalid");
  final ObjectMapper json = new ObjectMapper();
  byte[] envelope(String reply, String finish) throws Exception {
    return json.writeValueAsBytes(Map.of("candidates", List.of(Map.of("finishReason", finish, "content", Map.of("parts", List.of(Map.of("text", reply)))))));
  }
  String valid() { return "{\"gameQuery\":\"도타2\",\"category\":\"PATCH\",\"allCategories\":false,\"multipleCategories\":false,\"useCurrentGame\":false,\"command\":\"SEARCH\"}"; }
  @Test void onlySchemaValidSemanticSlotsAreAccepted() throws Exception {
    var slots = client.parse(envelope(valid(), "STOP"));
    assertEquals("도타2", slots.gameQuery()); assertEquals("PATCH", slots.category());
    for (String bad : List.of(valid().replace("\"gameQuery\":\"도타2\"", "\"appId\":570"),
        valid().replace("\"PATCH\"", "\"INVALID\""), valid().replace("\"allCategories\":false", "\"allCategories\":true"),
        valid().replace("\"useCurrentGame\":false", "\"useCurrentGame\":true"), valid().replace("\"SEARCH\"", "\"REPLAY\""),
        valid().replace("\"multipleCategories\":false", "\"multipleCategories\":true")))
      assertThrows(GameSearchException.class, () -> client.parse(envelope(bad, "STOP")));
    assertThrows(GameSearchException.class, () -> client.parse(envelope(valid(), "MAX_TOKENS")));
    assertThrows(GameSearchException.class, () -> client.parse("{}".getBytes()));
  }
  @Test void requestContainsNoCatalogOrIdsAndHasBoundedOutput() throws Exception {
    String body = json.writeValueAsString(client.requestBody("도타2 패치 알려줘"));
    assertTrue(body.contains("1024")); assertTrue(body.contains("도타2 패치 알려줘"));
    assertFalse(body.contains("69919")); assertFalse(body.contains("570"));
    assertThrows(GameSearchException.class, () -> client.extract("질문"));
  }
  @Test void localProviderStubChecksHttpSchemaAndQuotaWithoutExternalEgress() throws Exception {
    var status = new AtomicReference<>(200);
    var captured = new AtomicReference<String>();
    var keyHeader = new AtomicReference<String>();
    byte[] success = envelope(valid(), "STOP");
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/v1beta/models/test-model:generateContent", exchange -> {
      captured.set(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
      keyHeader.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
      byte[] reply = status.get() == 200 ? success : "{}".getBytes();
      exchange.sendResponseHeaders(status.get(), reply.length);
      try (var output = exchange.getResponseBody()) { output.write(reply); }
    });
    server.start();
    try {
      var local = new GeminiNewsVoiceIntentClient("test-only-key", "test-model", "http://127.0.0.1:" + server.getAddress().getPort());
      assertEquals("PATCH", local.extract("도타2 테스트 문장").category());
      assertEquals("test-only-key", keyHeader.get());
      var body = json.readTree(captured.get());
      assertEquals(1024, body.path("generationConfig").path("maxOutputTokens").asInt());
      assertTrue(body.path("generationConfig").path("responseSchema").isObject());
      status.set(429);
      var error = assertThrows(GameSearchException.class, () -> local.extract("도타2 테스트 문장"));
      assertEquals(429, error.getStatus().value());
    } finally { server.stop(0); }
  }
}
