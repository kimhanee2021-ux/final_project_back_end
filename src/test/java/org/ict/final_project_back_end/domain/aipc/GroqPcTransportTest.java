package org.ict.final_project_back_end.domain.aipc;

import org.ict.final_project_back_end.domain.aicommon.exception.AiException;
import org.ict.final_project_back_end.domain.aipc.client.GeminiPcClient;
import org.ict.final_project_back_end.domain.aipc.client.GroqPcTransport;
import org.ict.final_project_back_end.domain.aipc.service.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;

class GroqPcTransportTest {
  HttpServer server;
  GroqPcTransport transport;
  String reply, received;
  int status;
  boolean lowTokenHeader;

  @BeforeEach
  void setup() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/openai/v1/chat/completions",
        x -> {
          received = new String(x.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          var bytes = reply.getBytes(StandardCharsets.UTF_8);
          if (lowTokenHeader) {
            x.getResponseHeaders().add("x-ratelimit-remaining-tokens", "0");
            x.getResponseHeaders().add("x-ratelimit-reset-tokens", "60s");
          }
          x.sendResponseHeaders(status, bytes.length);
          x.getResponseBody().write(bytes);
          x.close();
        });
    server.start();
    transport =
        new GroqPcTransport(
            "test-only", "qwen/test", "http://127.0.0.1:" + server.getAddress().getPort());
    status = 200;
    reply =
        "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"{\\\"ok\\\":true}\"}}]}";
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  private com.fasterxml.jackson.databind.JsonNode ask() {
    return transport.ask(
        "한국어 JSON",
        List.of(Map.of("inlineData", Map.of("mimeType", "image/png", "data", "AQ=="))),
        Map.of("type", "object"));
  }

  @Test
  void translatesImageAndStructuredSchemaWithoutLeakingKeyIntoBody() throws Exception {
    assertTrue(ask().path("ok").asBoolean());
    var body = new ObjectMapper().readTree(received);
    assertEquals(
        "data:image/png;base64,AQ==",
        body.path("messages").get(1).path("content").get(0).path("image_url").path("url").asText());
    assertTrue(body.path("response_format").path("json_schema").path("strict").asBoolean());
    assertFalse(received.contains("test-only"));
    assertEquals("none", body.path("reasoning_effort").asText());
  }

  @Test
  void preservesQuotaStatusAndRejectsTruncatedAnswer() {
    status = 429;
    assertEquals(TOO_MANY_REQUESTS, assertThrows(AiException.class, this::ask).getStatus());
    status = 200;
    reply = "{\"choices\":[{\"finish_reason\":\"length\"}]}";
    assertEquals(BAD_GATEWAY, assertThrows(AiException.class, this::ask).getStatus());
  }

  @Test
  void rejectsInvalidJsonAndPermissionFailure() {
    reply = "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"broken\"}}]}";
    assertThrows(AiException.class, this::ask);
    status = 401;
    assertEquals(SERVICE_UNAVAILABLE, assertThrows(AiException.class, this::ask).getStatus());
  }

  @Test
  void configuredGroqIsSelectedWithoutGeminiFallback() {
    var groq = mock(GroqPcTransport.class);
    when(groq.configured()).thenReturn(true);
    when(groq.ask(anyString(), anyList(), anyMap()))
        .thenThrow(new AiException(TOO_MANY_REQUESTS, "test"));
    var client = new GeminiPcClient("unused", "test", "http://127.0.0.1:1", groq, "groq");
    assertEquals("Groq", client.provider());
    assertEquals(
        TOO_MANY_REQUESTS,
        assertThrows(AiException.class, () -> client.extract(new byte[] {1}, "image/png"))
            .getStatus());
    verify(groq).ask(anyString(), anyList(), anyMap());
  }

  @Test
  void parsesProviderResetDurationsWithoutGuessingMalformedValues() {
    assertEquals(7660, GroqPcTransport.resetDelayMillis("7.66s"));
    assertEquals(62500, GroqPcTransport.resetDelayMillis("1m2.5s"));
    assertEquals(20, GroqPcTransport.resetDelayMillis("20ms"));
    assertEquals(0, GroqPcTransport.resetDelayMillis("not-a-delay"));
  }

  @Test
  void explicitLegacyIntervalDoesNotSleepOrRejectNextRequest() {
    var connection =
        new GroqPcTransport(
            "test-only", "qwen/test", "http://127.0.0.1:" + server.getAddress().getPort(), 60);
    assertTimeoutPreemptively(
        java.time.Duration.ofSeconds(3),
        () -> {
          for (int i = 0; i < 2; i++)
            assertTrue(
                connection
                    .ask("JSON", List.of(Map.of("text", "data")), Map.of("type", "object"))
                    .path("ok")
                    .asBoolean());
        });
  }

  @Test
  void staleTokenHeaderDoesNotInventRateLimitOrDelay() {
    lowTokenHeader = true;
    assertTimeoutPreemptively(
        java.time.Duration.ofSeconds(3),
        () -> {
          assertTrue(ask().path("ok").asBoolean());
          assertTrue(ask().path("ok").asBoolean());
        });
  }
}
