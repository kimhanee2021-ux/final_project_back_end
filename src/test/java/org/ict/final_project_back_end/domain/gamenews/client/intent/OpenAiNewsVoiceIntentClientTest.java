package org.ict.final_project_back_end.domain.gamenews.client.intent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.ict.final_project_back_end.domain.gamenews.client.OpenAiResponsesTransport;
import org.ict.final_project_back_end.domain.gamenews.dto.NewsVoiceIntent.AiContext;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiNewsVoiceIntentClientTest {
  final ObjectMapper json = new ObjectMapper();
  final AiContext context = new AiContext(null, null, null, false, null, List.of());

  Map<String, Object> valid() {
    Map<String, Object> reply = new LinkedHashMap<>();
    reply.put("gameQuery", "하프 라이프 2");
    reply.put("category", "PATCH");
    reply.put("allCategories", false);
    reply.put("multipleCategories", false);
    reply.put("useCurrentGame", false);
    reply.put("command", "SEARCH");
    reply.put("gameQueries", List.of("Half-Life 2"));
    reply.put("gameAmbiguous", false);
    return reply;
  }

  @Test
  void oneHttpRequestExtractsIntentAndVariantsWithStrictSchema() throws Exception {
    var calls = new AtomicInteger();
    var payload = new AtomicReference<String>();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/v1/responses",
        exchange -> {
          calls.incrementAndGet();
          payload.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] bytes =
              json.writeValueAsBytes(
                  Map.of(
                      "status",
                      "completed",
                      "output",
                      List.of(
                          Map.of(
                              "content",
                              List.of(
                                  Map.of(
                                      "type",
                                      "output_text",
                                      "text",
                                      json.writeValueAsString(valid())))))));
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    try {
      var client =
          new OpenAiNewsVoiceIntentClient(
              new OpenAiResponsesTransport(
                  "test-key", "gpt-4.1-mini", "http://127.0.0.1:" + server.getAddress().getPort()));
      var slots = client.extract("하프 라이프 2 업데이트 내용 좀 정리해 줘", context);
      assertEquals("PATCH", slots.category());
      assertEquals(List.of("half-life 2"), slots.gameQueries());
      assertEquals(1, calls.get());
      var body = json.readTree(payload.get());
      assertFalse(body.path("store").asBoolean());
      assertTrue(body.path("text").path("format").path("strict").asBoolean());
      assertEquals(8, body.path("text").path("format").path("schema").path("required").size());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void invalidIdentityNumbersCommandsAndMissingAmbiguityAreRejected() {
    var client =
        new OpenAiNewsVoiceIntentClient(
            new OpenAiResponsesTransport("test-key", "gpt-4.1-mini", "http://localhost"));
    var reply = valid();
    reply.put("gameQueries", List.of("Half-Life 3"));
    assertThrows(
        RuntimeException.class,
        () -> client.parse(json.valueToTree(reply), "하프 라이프 2 패치", context));
    reply.put("gameQueries", List.of("Half-Life 2"));
    reply.put("gameQuery", "도타2");
    assertThrows(
        RuntimeException.class,
        () -> client.parse(json.valueToTree(reply), "하프 라이프 2 패치", context));
    reply.put("command", "STOP");
    assertThrows(
        RuntimeException.class, () -> client.parse(json.valueToTree(reply), "도타2 중지", context));
    reply.remove("gameAmbiguous");
    assertThrows(
        RuntimeException.class, () -> client.parse(json.valueToTree(reply), "도타2 중지", context));
  }

  @Test
  void coreContractAcceptsDirectJsonAndKeepsFieldAndConsistencyChecks() {
    var core = (com.fasterxml.jackson.databind.node.ObjectNode) json.valueToTree(valid());
    core.remove(List.of("gameQueries", "gameAmbiguous"));
    var slots = NewsVoiceIntentContract.parse(core);
    assertEquals("하프 라이프 2", slots.gameQuery());
    assertEquals("PATCH", slots.category());
    assertThrows(RuntimeException.class, () -> NewsVoiceIntentContract.parse(null));
    assertThrows(
        RuntimeException.class,
        () -> NewsVoiceIntentContract.parse(json.valueToTree(Map.of("candidates", List.of()))));
    var extra = core.deepCopy();
    extra.put("unknown", true);
    assertThrows(RuntimeException.class, () -> NewsVoiceIntentContract.parse(extra));
    var missing = core.deepCopy();
    missing.remove("category");
    assertThrows(RuntimeException.class, () -> NewsVoiceIntentContract.parse(missing));
    var wrongType = core.deepCopy();
    wrongType.put("allCategories", "false");
    assertThrows(RuntimeException.class, () -> NewsVoiceIntentContract.parse(wrongType));
    var conflict = core.deepCopy();
    conflict.put("allCategories", true);
    assertThrows(RuntimeException.class, () -> NewsVoiceIntentContract.parse(conflict));
    var stopped = core.deepCopy();
    stopped.put("command", "STOP");
    assertThrows(RuntimeException.class, () -> NewsVoiceIntentContract.parse(stopped));
  }
}
