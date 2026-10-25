package org.ict.final_project_back_end.domain.aipc;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.ict.final_project_back_end.domain.aicommon.exception.AiException;
import org.ict.final_project_back_end.domain.aipc.client.PcAiClient;
import org.ict.final_project_back_end.domain.aipc.client.PcAiProviderRouter;
import org.junit.jupiter.api.*;

class PcAiClientTest {
  @Test
  void libraryOverviewUsesAll49GamesInOneIndependentRequest() throws Exception {
    reply =
        "{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":\"{\\\"overallGrade\\\":\\\"B\\\"}\"}]}}]}";
    var pc =
        new org.ict.final_project_back_end.domain.aipc.dto.PcSpecs(
            "i7-13700", "", "UHD770", "", 32.0, "", 1000.0, 500.0, "");
    var games =
        java.util.stream.LongStream.rangeClosed(1, 49)
            .mapToObj(
                i ->
                    new org.ict.final_project_back_end.domain.aipc.dto.PcGameRequirements(
                        i,
                        "Game " + i,
                        "8GB RAM",
                        "",
                        true,
                        "https://store.steampowered.com/",
                        "today"))
            .toList();
    assertEquals(
        "B", client.overview(pc, games, java.util.List.of()).path("overallGrade").asText());
    var request = new ObjectMapper().readTree(received);
    var input =
        new ObjectMapper()
            .readTree(request.path("contents").get(0).path("parts").get(0).path("text").asText());
    assertEquals(49, input.path("games").size());
    assertEquals("Game 49", input.path("games").get(48).path("name").asText());
  }

  @Test
  void performanceRequestDoesNotAnchorEstimatesToConservativeRuleFailure() throws Exception {
    reply =
        "{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":\"{\\\"results\\\":[{\\\"appId\\\":730,\\\"grade\\\":\\\"O\\\"}]}\"}]}}]}";
    var pc =
        new org.ict.final_project_back_end.domain.aipc.dto.PcSpecs(
            "i7-13700", "", "UHD770", "", 32.0, "", 1000.0, 500.0, "");
    var game =
        new org.ict.final_project_back_end.domain.aipc.dto.PcGameRequirements(
            730L,
            "Counter-Strike 2",
            "GTX 1060; 8 GB RAM",
            "",
            true,
            "https://store.steampowered.com/",
            "today");
    var comparison =
        org.ict.final_project_back_end.domain.aipc.rule.PcRules.compare(pc, game.minimum());
    var answer =
        client.explainBatch(
            pc, java.util.List.of(game), java.util.List.of(comparison), java.util.List.of());
    var request = new ObjectMapper().readTree(received);
    var payload =
        new ObjectMapper()
            .readTree(request.path("contents").get(0).path("parts").get(0).path("text").asText());
    assertFalse(payload.path("games").get(0).has("ruleGrade"));
    assertFalse(payload.path("games").get(0).has("ruleChecks"));
    var schema =
        new ObjectMapper()
            .readTree(request.path("systemInstruction").path("parts").get(1).path("text").asText());
    assertFalse(
        schema.path("properties").path("results").path("items").path("properties").has("grade"));
    assertEquals(comparison.grade(), answer.path("results").get(0).path("grade").asText());
  }

  HttpServer server;
  PcAiClient client;
  String reply;
  int status;
  String received;

  @BeforeEach
  void setup() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        x -> {
          received = new String(x.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          var bytes = reply.getBytes(StandardCharsets.UTF_8);
          x.sendResponseHeaders(status, bytes.length);
          x.getResponseBody().write(bytes);
          x.close();
        });
    server.start();
    client =
        new PcAiClient(
            new PcAiProviderRouter(
                "test-only", "test-model", "http://127.0.0.1:" + server.getAddress().getPort()));
    status = 200;
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  String answer(String ram) throws Exception {
    String text =
        "{\"cpuModel\":\"i7-13700\",\"cpuDetails\":\"\",\"gpuModel\":\"Intel UHD Graphics"
            + " 770\",\"gpuMemory\":\"128MB 표시값\",\"ramGB\":\""
            + ram
            + "\",\"ramSpeed\":\"3200"
            + " MT/s\",\"storageTotalGB\":\"\",\"storageFreeGB\":\"\",\"os\":\"\",\"warnings\":[\"공유"
            + " 그래픽 메모리 확인 필요\"]}";
    return "{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":"
        + new ObjectMapper().writeValueAsString(text)
        + "}]}}]}";
  }

  @Test
  void extractsOnlyKnownFieldsAndKeepsMissingNull() throws Exception {
    reply = answer("32");
    var result = client.extract(new byte[] {1}, "image/png");
    assertEquals(32.0, result.specs().ramGB());
    assertNull(result.specs().storageFreeGB());
    assertTrue(received.contains("inlineData"));
    assertEquals(
        768,
        new ObjectMapper()
            .readTree(received)
            .path("generationConfig")
            .path("maxOutputTokens")
            .asInt());
    assertTrue(received.contains("장치ID"));
  }

  @Test
  void rejectsInventedOrNonFiniteCapacity() throws Exception {
    reply = answer("NaN");
    assertThrows(AiException.class, () -> client.extract(new byte[] {1}, "image/png"));
  }

  @Test
  void rateLimitIsNotReturnedAsSuccess() {
    status = 429;
    reply = "{}";
    assertThrows(AiException.class, () -> client.extract(new byte[] {1}, "image/png"));
  }

  @Test
  void truncatedResponseIsNotApplied() {
    reply = "{\"candidates\":[{\"finishReason\":\"MAX_TOKENS\"}]}";
    assertThrows(AiException.class, () -> client.extract(new byte[] {1}, "image/png"));
  }

  @Test
  void settingsCannotInventMeasuredFps() throws Exception {
    var data = new ObjectMapper().readTree("{\"settings\":[\"옵션을 낮추면 60 FPS 보장\"]}");
    assertThrows(AiException.class, () -> client.list(data, "settings"));
  }

  @Test
  void ignoresAiCalculatedStorageWithoutRawReading() throws Exception {
    var json = new ObjectMapper();
    var outer = json.readTree(answer("32"));
    var inner =
        (com.fasterxml.jackson.databind.node.ObjectNode)
            json.readTree(
                outer
                    .path("candidates")
                    .get(0)
                    .path("content")
                    .path("parts")
                    .get(0)
                    .path("text")
                    .asText());
    inner.put("storageTotalGB", "1413");
    inner.put("storageFreeGB", "1392");
    inner.put("storageTotalText", "");
    inner.put("storageUsedText", "");
    inner.put("storageFreeText", "");
    ((com.fasterxml.jackson.databind.node.ObjectNode)
            outer.path("candidates").get(0).path("content").path("parts").get(0))
        .put("text", json.writeValueAsString(inner));
    reply = outer.toString();
    var result = client.extract(new byte[] {1}, "image/png");
    assertNull(result.specs().storageTotalGB());
    assertNull(result.specs().storageFreeGB());
  }

  @Test
  void batchPacksFiveGamesInOneProviderRequest() throws Exception {
    reply =
        "{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":\"{\\\"results\\\":[]}\"}]}}]}";
    var pc =
        new org.ict.final_project_back_end.domain.aipc.dto.PcSpecs(
            "i7-13700", "", "UHD770", "", 32.0, "", 1000.0, 500.0, "");
    var games =
        java.util.stream.LongStream.rangeClosed(1, 5)
            .mapToObj(
                i ->
                    new org.ict.final_project_back_end.domain.aipc.dto.PcGameRequirements(
                        i,
                        "game",
                        "4 GB RAM",
                        "",
                        true,
                        "https://store.steampowered.com/",
                        "today"))
            .toList();
    var comparisons =
        games.stream()
            .map(
                g ->
                    org.ict.final_project_back_end.domain.aipc.rule.PcRules.compare(
                        pc, g.minimum()))
            .toList();
    client.explainBatch(pc, games, comparisons, java.util.List.of());
    var request = new ObjectMapper().readTree(received);
    String input = request.path("contents").get(0).path("parts").get(0).path("text").asText();
    assertEquals(5, new ObjectMapper().readTree(input).path("games").size());
    assertTrue(received.contains("results"));
    assertFalse(request.path("generationConfig").has("responseJsonSchema"));
    assertEquals(
        "application/json", request.path("generationConfig").path("responseMimeType").asText());
    var schema =
        new ObjectMapper()
            .readTree(request.path("systemInstruction").path("parts").get(1).path("text").asText());
    var fields = schema.path("properties").path("results").path("items").path("properties");
    assertTrue(fields.has("appId"));
    assertFalse(fields.has("analysis"));
    assertEquals("array", fields.path("targets").path("type").asText());
  }

  @Test
  void geminiQuotaFallsBackToGroqOnceAndReportsActualProvider() throws Exception {
    var groq =
        org.mockito.Mockito.mock(
            org.ict.final_project_back_end.domain.aipc.client.GroqPcTransport.class);
    org.mockito.Mockito.when(groq.configured()).thenReturn(true);
    var extractionReply =
        new ObjectMapper()
            .readTree(
                new ObjectMapper()
                    .readTree(answer("32"))
                    .path("candidates")
                    .get(0)
                    .path("content")
                    .path("parts")
                    .get(0)
                    .path("text")
                    .asText());
    org.mockito.Mockito.when(
            groq.ask(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyMap()))
        .thenReturn(extractionReply);
    var routed =
        new PcAiClient(
            new PcAiProviderRouter(
                "test-only",
                "test-model",
                "http://127.0.0.1:" + server.getAddress().getPort(),
                groq,
                "auto"));
    status = 429;
    reply = "{}";
    assertEquals("Groq ↔ Gemini", routed.provider());
    assertEquals("Groq", routed.extract(new byte[] {1}, "image/png").aiProvider());
    org.mockito.Mockito.verify(groq, org.mockito.Mockito.times(1))
        .ask(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyList(),
            org.mockito.ArgumentMatchers.anyMap());
  }

  @Test
  void geminiSuccessDoesNotTransmitPhotoToGroq() throws Exception {
    var groq =
        org.mockito.Mockito.mock(
            org.ict.final_project_back_end.domain.aipc.client.GroqPcTransport.class);
    org.mockito.Mockito.when(groq.configured()).thenReturn(true);
    var routed =
        new PcAiClient(
            new PcAiProviderRouter(
                "test-only",
                "test-model",
                "http://127.0.0.1:" + server.getAddress().getPort(),
                groq,
                "auto"));
    reply = answer("32");
    assertEquals("Gemini", routed.extract(new byte[] {1}, "image/png").aiProvider());
    org.mockito.Mockito.verify(groq, org.mockito.Mockito.never())
        .ask(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyList(),
            org.mockito.ArgumentMatchers.anyMap());
  }

  @Test
  void malformedGeminiResponseDoesNotTriggerFallback() {
    var groq =
        org.mockito.Mockito.mock(
            org.ict.final_project_back_end.domain.aipc.client.GroqPcTransport.class);
    org.mockito.Mockito.when(groq.configured()).thenReturn(true);
    var routed =
        new PcAiClient(
            new PcAiProviderRouter(
                "test-only",
                "test-model",
                "http://127.0.0.1:" + server.getAddress().getPort(),
                groq,
                "auto"));
    reply = "{}";
    assertThrows(AiException.class, () -> routed.extract(new byte[] {1}, "image/png"));
    org.mockito.Mockito.verify(groq, org.mockito.Mockito.never())
        .ask(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyList(),
            org.mockito.ArgumentMatchers.anyMap());
  }

  @Test
  void smallTextUsesGroqAndQuotaFallsBackOnce() throws Exception {
    var groq =
        org.mockito.Mockito.mock(
            org.ict.final_project_back_end.domain.aipc.client.GroqPcTransport.class);
    org.mockito.Mockito.when(groq.configured()).thenReturn(true);
    var expected = new ObjectMapper().readTree("{\"ok\":true}");
    org.mockito.Mockito.when(
            groq.ask(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyMap(),
                org.mockito.ArgumentMatchers.anyInt()))
        .thenReturn(expected);
    var routed =
        new PcAiProviderRouter(
            "test-only",
            "test-model",
            "http://127.0.0.1:" + server.getAddress().getPort(),
            groq,
            "auto");
    var parts = java.util.List.<java.util.Map<String, Object>>of(java.util.Map.of("text", "짧은 자료"));
    assertEquals(expected, routed.ask("짧은 설명", parts, java.util.Map.of(), 900));
    assertEquals("Groq", routed.actualProvider());
    assertNull(received);
    org.mockito.Mockito.when(
            groq.ask(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyMap(),
                org.mockito.ArgumentMatchers.anyInt()))
        .thenThrow(new AiException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "limit"));
    reply = answer("32");
    routed.ask("짧은 설명", parts, java.util.Map.of(), 900);
    assertEquals("Gemini", routed.actualProvider());
    org.mockito.Mockito.verify(groq, org.mockito.Mockito.times(2))
        .ask(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyList(),
            org.mockito.ArgumentMatchers.anyMap(),
            org.mockito.ArgumentMatchers.anyInt());
  }

  @Test
  void pcLiteModelUsesMinimalThinkingInsteadOfShrinkingTimeoutOnly() throws Exception {
    client =
        new PcAiClient(
            new PcAiProviderRouter(
                "test-only",
                "gemini-3.5-flash-lite",
                "http://127.0.0.1:" + server.getAddress().getPort()));
    reply = answer("32");
    client.extract(new byte[] {1}, "image/png");
    var config = new ObjectMapper().readTree(received).path("generationConfig");
    assertEquals("minimal", config.path("thinkingConfig").path("thinkingLevel").asText());
    assertEquals(768, config.path("maxOutputTokens").asInt());
  }
}
