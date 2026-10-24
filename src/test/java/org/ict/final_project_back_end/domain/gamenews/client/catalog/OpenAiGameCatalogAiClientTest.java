package org.ict.final_project_back_end.domain.gamenews.client.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.ict.final_project_back_end.domain.game.service.GameNameResolver.Candidate;
import org.ict.final_project_back_end.domain.gamenews.client.OpenAiResponsesTransport;
import org.ict.final_project_back_end.domain.gamenews.dto.NewsVoiceIntent.AiContext;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiGameCatalogAiClientTest {
  final ObjectMapper json = new ObjectMapper();
  final AiContext context = new AiContext(null, null, null, false, null, List.of());

  @Test
  void structuredRequestLimitsPayloadAndValidatesOutOfRangeSelection() throws Exception {
    var reply = new AtomicReference<>("{\"queries\":[\"Elden Ring\"]}");
    var request = new AtomicReference<String>();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/v1/responses",
        exchange -> {
          request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] bytes =
              json.writeValueAsBytes(
                  Map.of(
                      "status",
                      "completed",
                      "output",
                      List.of(
                          Map.of(
                              "content",
                              List.of(Map.of("type", "output_text", "text", reply.get()))))));
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    try {
      var client =
          new OpenAiGameCatalogAiClient(
              new OpenAiResponsesTransport(
                  "test-key", "gpt-4.1-mini", "http://127.0.0.1:" + server.getAddress().getPort()));
      assertEquals(List.of("elden ring"), client.expand("엘든 링", context));
      var body = json.readTree(request.get());
      assertFalse(body.path("store").asBoolean());
      assertTrue(body.path("text").path("format").path("strict").asBoolean());
      reply.set("{\"selectedIndex\":1,\"ambiguous\":false}");
      assertEquals(
          1,
          client
              .choose(
                  "엘든 링 패치", context, List.of(new Candidate(910003, "ELDEN RING", "MODEL_QUERY")))
              .selectedIndex());
      assertFalse(request.get().contains("910003"));
      reply.set("{\"selectedIndex\":99,\"ambiguous\":false}");
      assertThrows(
          RuntimeException.class,
          () ->
              client.choose(
                  "엘든 링", context, List.of(new Candidate(910003, "ELDEN RING", "MODEL_QUERY"))));
    } finally {
      server.stop(0);
    }
  }
}
