package org.ict.final_project_back_end.domain.gamenews.client;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OpenAiNewsTranscriptionClientTest {
  @Test void sendsAudioAndBilingualHintsWithoutSingleLanguageLockAndReadsTranscript() throws Exception {
    var body = new AtomicReference<String>();
    var auth = new AtomicReference<String>();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/v1/audio/transcriptions", exchange -> {
      body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
      byte[] response = "{\"text\":\"카운터스트라이크2 업데이트 알려줘\"}".getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, response.length); exchange.getResponseBody().write(response); exchange.close();
    });
    server.start();
    try {
      var client = new OpenAiNewsTranscriptionClient("test-key", "gpt-transcribe", "http://127.0.0.1:" + server.getAddress().getPort());
      assertEquals("카운터스트라이크2 업데이트 알려줘", client.transcribe("audio-fixture".getBytes(), "audio/webm", List.of("Counter-Strike 2", "카운터스트라이크2")));
      assertEquals("Bearer test-key", auth.get());
      assertTrue(body.get().contains("name=\"languages[]\"\r\n\r\nko"));
      assertTrue(body.get().contains("name=\"languages[]\"\r\n\r\nen"));
      assertTrue(body.get().contains("name=\"keywords[]\""));
      assertTrue(body.get().contains("filename=\"speech.webm\""));
      assertFalse(body.get().contains("name=\"language\""));
    } finally { server.stop(0); }
  }
  @Test void blankKeyFailsBeforeNetworkAndProviderErrorDoesNotLeakBody() throws Exception {
    assertThrows(RuntimeException.class, () -> new OpenAiNewsTranscriptionClient("", "gpt-transcribe", "http://invalid")
        .transcribe(new byte[0], "audio/wav", List.of()));
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/v1/audio/transcriptions", exchange -> {
      exchange.getRequestBody().readAllBytes();
      byte[] bytes = "provider-private-detail".getBytes();
      exchange.sendResponseHeaders(500, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
    });
    server.start();
    try {
      var error = assertThrows(RuntimeException.class, () -> new OpenAiNewsTranscriptionClient("test-key", "gpt-transcribe",
          "http://127.0.0.1:" + server.getAddress().getPort()).transcribe(new byte[0], "audio/wav", List.of()));
      assertFalse(error.getMessage().contains("provider-private-detail"));
    } finally { server.stop(0); }
  }
}
