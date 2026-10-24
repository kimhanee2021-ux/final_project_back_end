package org.ict.final_project_back_end.domain.gamenews.client;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.*;
import org.ict.final_project_back_end.domain.gamenews.exception.BbsVoiceException;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class OpenAiBbsTranscriptionClientTest {
  HttpServer server;
  URI endpoint;
  AtomicReference<String> request = new AtomicReference<>(), auth = new AtomicReference<>();
  AtomicReference<String> reply = new AtomicReference<>("{\"text\":\" Counter-Strike 2 \"}");
  AtomicInteger code = new AtomicInteger(200), calls = new AtomicInteger();
  @BeforeEach void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/v1/audio/transcriptions", exchange -> {
      calls.incrementAndGet();
      auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
      request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      byte[] body = reply.get().getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(code.get(), body.length);
      try (var output = exchange.getResponseBody()) { output.write(body); }
    });
    server.start();
    endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/audio/transcriptions");
  }
  @AfterEach void stop() { server.stop(0); }
  OpenAiBbsTranscriptionClient client(String key, String model) { return new OpenAiBbsTranscriptionClient(key, model, HttpClient.newHttpClient(), endpoint); }

  @Test void sendsBoundedRecordingToOpenAiMultipartContract() {
    assertEquals("Counter-Strike 2", client("test-key", "gpt-transcribe").transcribe(new byte[12], "audio/webm"));
    assertEquals("Bearer test-key", auth.get());
    assertTrue(request.get().contains("name=\"model\"\r\n\r\ngpt-transcribe"));
    assertTrue(request.get().contains("filename=\"speech.webm\""));
    assertTrue(request.get().contains("name=\"languages[]\"\r\n\r\nko"));
    assertTrue(request.get().contains("name=\"languages[]\"\r\n\r\nen"));
    assertFalse(request.get().contains("name=\"language\""));
    assertEquals(1, calls.get());
  }
  @Test void missingKeyOrNonGptModelNeverCallsProvider() {
    for (var client : new OpenAiBbsTranscriptionClient[]{client("", "gpt-transcribe"), client("test", "gemini-model")}) {
      assertFalse(client.configured());
      assertEquals("BBS_STT_NOT_CONFIGURED", assertThrows(BbsVoiceException.class, () -> client.transcribe(new byte[12], "audio/webm")).getCode());
    }
    assertEquals(0, calls.get());
  }
  @Test void providerFailuresDoNotLeakBodyOrRetry() {
    reply.set("secret upstream diagnostic");
    for (int status : new int[]{401, 403, 429, 500}) {
      code.set(status);
      var ex = assertThrows(BbsVoiceException.class, () -> client("test", "gpt-transcribe").transcribe(new byte[12], "audio/mp4"));
      assertEquals(status == 429 ? 429 : status < 429 ? 503 : 502, ex.getStatus().value());
      assertFalse(ex.getMessage().contains("secret"));
    }
    assertEquals(4, calls.get());
  }
  @Test void malformedEmptyLongAndOversizedResponsesAreRejected() {
    for (String value : new String[]{"null", "{}", "[]", "{\"text\":17}", "not json", "{\"text\":\"  \"}", "{\"text\":\"" + "x".repeat(256) + "\"}", "x".repeat(70000)}) {
      reply.set(value);
      assertThrows(BbsVoiceException.class, () -> client("test", "gpt-transcribe").transcribe(new byte[12], "audio/wav"));
    }
  }
}
