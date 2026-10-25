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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Groq(OpenAI 호환 chat/completions) 호출만 맡는다. 키는 서버 .env의 GROQ_API_KEY만 쓴다.
// Gemini와 한도가 따로라서, 한쪽이 혼잡하거나 한도에 걸려도 다른 쪽으로 답할 수 있다.
@Slf4j
@Component
public class GroqCoachModel implements CoachModel {

  private static final int MAX_RESPONSE_BYTES = 131_072;

  private final String apiKey;
  private final String model;
  private final String baseUrl;
  private final ObjectMapper json = new ObjectMapper();
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  public GroqCoachModel(@Value("${app.coach.groq-api-key:}") String apiKey,
                        @Value("${app.coach.groq-model:qwen/qwen3.8-27b}") String model,
                        @Value("${app.coach.groq-base-url:https://api.groq.com}") String baseUrl) {
    this.apiKey = apiKey;
    this.model = model;
    this.baseUrl = baseUrl.replaceAll("/+$", "");
  }

  @Override
  public String provider() {
    return "groq";
  }

  @Override
  public boolean configured() {
    return apiKey != null && !apiKey.isBlank() && model.matches("[a-zA-Z0-9/._-]{1,100}");
  }

  @Override
  public String generateJson(Request request) {
    if (!configured()) {
      throw AiCoachException.notConfigured();
    }
    try {
      HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(baseUrl + "/openai/v1/chat/completions"))
          .timeout(Duration.ofSeconds(30))
          .header("Content-Type", "application/json")
          .header("Authorization", "Bearer " + apiKey)
          .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(requestBody(request))))
          .build();
      HttpResponse<byte[]> response = http.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
      // 혼잡·한도 초과는 여기서 다시 시도하지 않고, 라우터가 다른 공급자로 넘기도록 바로 알린다
      if (CoachModel.isServerBusy(response.statusCode())) {
        throw AiCoachException.busy();
      }
      if (response.statusCode() == 429) {
        throw AiCoachException.tooManyRequests();
      }
      if (response.statusCode() == 401 || response.statusCode() == 403 || response.statusCode() == 404) {
        throw AiCoachException.notConfigured();
      }
      if (response.statusCode() != 200 || response.body().length > MAX_RESPONSE_BYTES) {
        log.warn("Groq 코치 응답 거부: HTTP {}, {} bytes", response.statusCode(), response.body().length);
        throw AiCoachException.invalidResponse();
      }
      return answerText(json, response.body());
    } catch (HttpTimeoutException e) {
      throw AiCoachException.timeout();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw AiCoachException.timeout();
    } catch (IOException e) {
      log.warn("Groq 코치 호출 실패: {}", e.getClass().getSimpleName());
      throw AiCoachException.invalidResponse();
    }
  }

  Map<String, Object> requestBody(Request request) {
    List<Map<String, Object>> messages = new ArrayList<>();
    messages.add(Map.of("role", "system", "content", request.systemPrompt()));
    for (Message message : request.messages()) {
      // Gemini의 model 역할을 OpenAI 형식의 assistant로 바꾼다
      messages.add(Map.of("role", "model".equals(message.role()) ? "assistant" : "user", "content", message.text()));
    }
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", model);
    body.put("messages", messages);
    // strict를 켜면 maxItems 같은 일부 규칙을 받지 않는 모델이 있어 끄고, 형식은 부르는 쪽이 다시 검사한다
    body.put("response_format", Map.of("type", "json_schema",
        "json_schema", Map.of("name", "coach_answer", "strict", false, "schema", request.schema())));
    body.put("temperature", request.temperature());
    body.put("max_completion_tokens", request.maxOutputTokens());
    if (model.startsWith("qwen/")) {
      // 생각 단계를 건너뛰어 답을 빨리 받는다 (PC 진단의 Groq 설정과 같다)
      body.put("reasoning_effort", "none");
    }
    return body;
  }

  // chat/completions 응답에서 답 문자열만 꺼낸다. 끝까지 생성되지 않았거나 거절했으면 버린다
  static String answerText(ObjectMapper json, byte[] body) throws IOException {
    JsonNode choice = json.readTree(body).path("choices").path(0);
    if (!"stop".equals(choice.path("finish_reason").asText())
        || !choice.path("message").path("refusal").asText("").isBlank()) {
      log.warn("Groq 코치 응답 거부: 생성 중단 {}", choice.path("finish_reason").asText());
      throw AiCoachException.invalidResponse();
    }
    return choice.path("message").path("content").asText("");
  }
}
