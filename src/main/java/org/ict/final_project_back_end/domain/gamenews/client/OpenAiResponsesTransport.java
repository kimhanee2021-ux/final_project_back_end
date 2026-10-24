package org.ict.final_project_back_end.domain.gamenews.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ict.final_project_back_end.domain.game.service.GameSearchException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

// Responses API의 HTTP 통신을 공유하며 프롬프트, 응답 스키마, 결과 검증은 각 클라이언트가 담당합니다.
@Component
public class OpenAiResponsesTransport {
  private final String key, model, base;
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
  private final ObjectMapper json = new ObjectMapper();

  public OpenAiResponsesTransport(
      @Value("${app.news.catalog.api-key:${OPENAI_API_KEY:}}") String key,
      @Value("${app.news.catalog.model:${OPENAI_CATALOG_MODEL:gpt-4.1-mini}}") String model,
      @Value("${app.news.catalog.base-url:https://api.openai.com}") String base) {
    this.key = key;
    this.model = model;
    this.base = base;
  }

  public JsonNode generate(
      String instructions, Map<String, Object> input, Map<String, Object> schema) {
    if (key.isBlank() || !model.matches("[a-zA-Z0-9._-]{1,100}")) throw unavailable();
    try {
      var body =
          Map.of(
              "model",
              model,
              "store",
              false,
              "instructions",
              instructions,
              "input",
              json.writeValueAsString(input),
              "max_output_tokens",
              800,
              "text",
              Map.of(
                  "format",
                  Map.of(
                      "type",
                      "json_schema",
                      "name",
                      "catalog_match",
                      "strict",
                      true,
                      "schema",
                      schema)));
      var request =
          HttpRequest.newBuilder(URI.create(base + "/v1/responses"))
              .timeout(Duration.ofSeconds(10))
              .header("Content-Type", "application/json")
              .header("Authorization", "Bearer " + key)
              .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
              .build();
      var future = http.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray());
      HttpResponse<byte[]> response;
      try {
        response = future.get(10, TimeUnit.SECONDS);
      } catch (TimeoutException error) {
        future.cancel(true);
        throw unavailable();
      }
      if (response.statusCode() != 200 || response.body().length > 65536) throw unavailable();
      var root = json.readTree(response.body());
      if (root == null || !root.path("status").asText().equals("completed")) throw unavailable();
      StringBuilder text = new StringBuilder();
      for (var item : root.path("output"))
        for (var part : item.path("content")) {
          if (part.path("type").asText().equals("refusal")) throw unavailable();
          if (part.path("type").asText().equals("output_text"))
            text.append(part.path("text").asText());
        }
      var reply = json.readTree(text.toString());
      if (reply == null || !reply.isObject()) throw unavailable();
      return reply;
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw unavailable();
    } catch (IOException | ExecutionException | IllegalArgumentException error) {
      throw unavailable();
    }
  }

  private GameSearchException unavailable() {
    return new GameSearchException(
        HttpStatus.SERVICE_UNAVAILABLE,
        "CATALOG_AI_UNAVAILABLE",
        "게임명을 확정하지 못했어요. 후보를 선택하거나 더 구체적인 이름을 말씀해 주세요.");
  }
}
