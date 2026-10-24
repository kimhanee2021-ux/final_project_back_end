package org.ict.final_project_back_end.domain.gamenews.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.ict.final_project_back_end.domain.game.service.GameSearchException;
import org.ict.final_project_back_end.domain.gamenews.client.catalog.OpenAiGameCatalogAiClient;
import org.ict.final_project_back_end.domain.gamenews.client.intent.OpenAiNewsVoiceIntentClient;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenAiResponsesTransportTest {
  @Test
  void bothFeatureClientsReceiveTheSameSpringTransport() {
    try (var context =
        new AnnotationConfigApplicationContext(
            OpenAiResponsesTransport.class,
            OpenAiGameCatalogAiClient.class,
            OpenAiNewsVoiceIntentClient.class)) {
      var transport = context.getBean(OpenAiResponsesTransport.class);
      assertSame(
          transport,
          ReflectionTestUtils.getField(
              context.getBean(OpenAiGameCatalogAiClient.class), "transport"));
      assertSame(
          transport,
          ReflectionTestUtils.getField(
              context.getBean(OpenAiNewsVoiceIntentClient.class), "transport"));
    }
  }

  @Test
  void providerFailuresKeepExistingUnavailableErrorWithoutExtraRequests() throws Exception {
    var json = new ObjectMapper();
    var status = new AtomicInteger(200);
    var reply = new AtomicReference<>("{}");
    var calls = new AtomicInteger();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/v1/responses",
        exchange -> {
          calls.incrementAndGet();
          exchange.getRequestBody().readAllBytes();
          byte[] bytes = reply.get().getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(status.get(), bytes.length);
          try (var output = exchange.getResponseBody()) {
            output.write(bytes);
          }
        });
    server.start();
    try {
      String base = "http://127.0.0.1:" + server.getAddress().getPort();
      var client = new OpenAiResponsesTransport("test-key", "gpt-4.1-mini", base);
      var schema = Map.<String, Object>of("type", "object");
      var missing = new OpenAiResponsesTransport("", "gpt-4.1-mini", base);
      assertThrows(GameSearchException.class, () -> missing.generate("prompt", Map.of(), schema));
      assertEquals(0, calls.get());
      List<String> failures =
          List.of(
              "not json",
              "{\"status\":\"incomplete\",\"output\":[]}",
              json.writeValueAsString(
                  Map.of(
                      "status",
                      "completed",
                      "output",
                      List.of(Map.of("content", List.of(Map.of("type", "refusal")))))));
      for (String failure : failures) {
        reply.set(failure);
        var error =
            assertThrows(
                GameSearchException.class, () -> client.generate("prompt", Map.of(), schema));
        assertEquals("CATALOG_AI_UNAVAILABLE", error.getCode());
      }
      status.set(503);
      var error =
          assertThrows(
              GameSearchException.class, () -> client.generate("prompt", Map.of(), schema));
      assertEquals("CATALOG_AI_UNAVAILABLE", error.getCode());
      assertEquals(4, calls.get());
    } finally {
      server.stop(0);
    }
  }
}
