package org.ict.final_project_back_end.domain.aicoach.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.ict.final_project_back_end.domain.aicoach.exception.AiCoachException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

// Gemini(generateContent) 호출만 맡는다. 키는 서버 .env의 GEMINI_API_KEY만 쓰고 프론트로 보내지 않는다.
// 무엇을 물을지(프롬프트)와 답이 맞는지(형식 검사)는 CoachReplyClient, CoachInsightClient가 정한다.
@Slf4j
@Component
public class GeminiCoachModel implements CoachModel {

  private static final int MAX_RESPONSE_BYTES = 131_072;
  private static final int MAX_RETRIES = 2;
  private static final long RETRY_DELAY_MS = 1_500;

  private final String apiKey;
  private final String model;
  private final String baseUrl;
  private final ObjectMapper json = new ObjectMapper();
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  public GeminiCoachModel(@Value("${app.coach.api-key:}") String apiKey,
                          @Value("${app.coach.model:gemini-3.5-flash-lite}") String model,
                          @Value("${app.coach.base-url:https://generativelanguage.googleapis.com}") String baseUrl) {
    this.apiKey = apiKey;
    this.model = model;
    this.baseUrl = baseUrl;
  }

  @Override
  public String provider() {
    return "gemini";
  }

  @Override
  public boolean configured() {
    return apiKey != null && !apiKey.isBlank() && model.matches("[a-zA-Z0-9._-]{1,100}");
  }

  // Gemini를 한 번 부르고 답 문자열을 돌려준다 (혼잡하면 잠깐 기다렸다 다시 시도)
  @Override
  public String generateJson(Request request) {
    if (!configured()) {
      throw AiCoachException.notConfigured();
    }
    try {
      HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(baseUrl + "/v1beta/models/" + model + ":generateContent"))
          .timeout(Duration.ofSeconds(30))
          .header("Content-Type", "application/json")
          .header("x-goog-api-key", apiKey)
          .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(requestBody(request))))
          .build();
      HttpResponse<byte[]> response = http.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
      // Gemini가 잠깐 혼잡(500/502/503/504)하면 조금 기다렸다가 다시 묻는다
      for (int retry = 1; CoachModel.isServerBusy(response.statusCode()) && retry <= MAX_RETRIES; retry++) {
        log.info("Gemini 코치 혼잡 HTTP {}, {}번째 다시 시도", response.statusCode(), retry);
        Thread.sleep(RETRY_DELAY_MS * retry);
        response = http.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
      }
      if (CoachModel.isServerBusy(response.statusCode())) {
        log.warn("Gemini 코치 혼잡이 계속됨: HTTP {}", response.statusCode());
        throw AiCoachException.busy();
      }
      if (response.statusCode() == 429) {
        throw AiCoachException.tooManyRequests();
      }
      if (response.statusCode() == 401 || response.statusCode() == 403 || response.statusCode() == 404) {
        throw AiCoachException.notConfigured();
      }
      if (response.statusCode() != 200 || response.body().length > MAX_RESPONSE_BYTES) {
        log.warn("Gemini 코치 응답 거부: HTTP {}, {} bytes", response.statusCode(), response.body().length);
        throw AiCoachException.invalidResponse();
      }
      return answerText(json, response.body());
    } catch (HttpTimeoutException e) {
      throw AiCoachException.timeout();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw AiCoachException.timeout();
    } catch (IOException e) {
      log.warn("Gemini 코치 호출 실패: {}", e.getClass().getSimpleName());
      throw AiCoachException.invalidResponse();
    }
  }

  Map<String, Object> requestBody(Request request) {
    List<Map<String, Object>> contents = request.messages().stream()
        .map(message -> Map.<String, Object>of("role", message.role(), "parts", List.of(Map.of("text", message.text()))))
        .toList();
    return Map.of(
        "systemInstruction", Map.of("parts", List.of(Map.of("text", request.systemPrompt()))),
        "contents", contents,
        "generationConfig", Map.of("responseMimeType", "application/json", "responseJsonSchema", request.schema(),
            "temperature", request.temperature(), "maxOutputTokens", request.maxOutputTokens()));
  }

  // Gemini 응답 봉투에서 답 문자열만 꺼낸다. 끝까지 생성되지 않았으면(MAX_TOKENS 등) 버린다
  static String answerText(ObjectMapper json, byte[] body) throws IOException {
    JsonNode candidate = json.readTree(body).path("candidates").path(0);
    if (!"STOP".equals(candidate.path("finishReason").asText())) {
      log.warn("Gemini 코치 응답 거부: 생성 중단 {}", candidate.path("finishReason").asText());
      throw AiCoachException.invalidResponse();
    }
    StringBuilder text = new StringBuilder();
    for (JsonNode part : candidate.path("content").path("parts")) {
      if (!part.path("thought").asBoolean(false)) {
        text.append(part.path("text").asText(""));
      }
    }
    return text.toString();
  }
}
