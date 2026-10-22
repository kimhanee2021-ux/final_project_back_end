package org.ict.final_project_back_end.domain.aiPc.service;

import static org.springframework.http.HttpStatus.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.ict.final_project_back_end.domain.aiCommon.exception.AiException;
import org.ict.final_project_back_end.domain.aiCommon.service.GeminiSupport;

/** PC 기능의 Gemini generateContent 호출과 응답 봉투 해석만 담당한다. 공급자 선택은 GeminiPcClient가 한다. */
public class GeminiPcTransport extends GeminiSupport {

  public GeminiPcTransport(String key, String model, String base) {
    super(key, model, base);
  }

  public JsonNode ask(
      String instructions,
      List<Map<String, Object>> parts,
      Map<String, Object> schema,
      int outputLimit) {
    if (key.isBlank() || !model.matches("[a-zA-Z0-9._-]{1,100}"))
      throw new AiException(SERVICE_UNAVAILABLE, "AI 연결 설정을 확인해주세요.");
    try {
      // 현재 모델은 이 중첩 responseJsonSchema를 HTTP400으로 거절한다. JSON모드+서버 검증을 사용한다.
      var generation = new LinkedHashMap<String, Object>();
      generation.put("responseMimeType", "application/json");
      generation.put("maxOutputTokens", Math.max(768, Math.min(4096, outputLimit)));
      if (model.equals("gemini-3.5-flash-lite") || model.equals("gemini-3.1-flash-lite"))
        generation.put("thinkingConfig", Map.of("thinkingLevel", "minimal"));
      log.info(
          "PC Gemini request model={}, maxOutputTokens={}, minimalThinking={}",
          model,
          outputLimit,
          generation.containsKey("thinkingConfig"));
      var body =
          Map.of(
              "systemInstruction",
              Map.of(
                  "parts",
                  List.of(
                      Map.of("text", instructions + "\n다음 JSON 스키마의 필수항목과형식을지켜 JSON객체만출력하세요."),
                      Map.of("text", json.writeValueAsString(schema)))),
              "contents",
              List.of(Map.of("role", "user", "parts", parts)),
              "generationConfig",
              generation);
      var req =
          HttpRequest.newBuilder(
                  URI.create(baseUrl + "/v1beta/models/" + model + ":generateContent"))
              .timeout(Duration.ofSeconds(45))
              .header("Content-Type", "application/json")
              .header("x-goog-api-key", key)
              .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
              .build();
      var response = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
      if (response.statusCode() != 200)
        log.warn("PC Gemini request failed: HTTP {}", response.statusCode());
      if (response.statusCode() == 400 && response.body().length <= 131072) {
        var error = json.readTree(response.body()).path("error");
        String lower = error.path("message").asText("").toLowerCase(Locale.ROOT);
        boolean keyFailure =
            lower.contains("api key")
                && (lower.contains("invalid")
                    || lower.contains("expired")
                    || lower.contains("not valid"));
        boolean schemaFailure = lower.contains("schema") || lower.contains("generationconfig");
        boolean locationFailure = lower.contains("location") && lower.contains("not supported");
        log.warn(
            "PC Gemini rejected request: schemaRelated={}, keyRelated={}, locationRelated={}",
            schemaFailure,
            keyFailure,
            locationFailure);
        if (keyFailure || locationFailure)
          throw new AiException(SERVICE_UNAVAILABLE, "Gemini 연결 설정을 사용할 수 없어 예비 AI를 확인해요.");
      }
      if (response.statusCode() == 401
          || response.statusCode() == 403
          || response.statusCode() == 404
          || response.statusCode() == 503
          || response.statusCode() == 502
          || response.statusCode() == 504)
        throw new AiException(SERVICE_UNAVAILABLE, "Gemini가 혼잡해요.");
      if (response.statusCode() == 429)
        throw new AiException(TOO_MANY_REQUESTS, "AI 이용 한도에 도달했어요. 잠시 후 다시 시도해주세요.");
      if (response.statusCode() != 200 || response.body().length > 131072) throw invalid();
      var envelope = json.readTree(response.body());
      var usage = envelope.path("usageMetadata");
      log.info(
          "PC AI usage provider=Gemini model={} inputTokens={} outputTokens={} totalTokens={}",
          model,
          usage.path("promptTokenCount").asLong(-1),
          usage.path("candidatesTokenCount").asLong(-1),
          usage.path("totalTokenCount").asLong(-1));
      var candidate = envelope.path("candidates").path(0);
      if (!"STOP".equals(candidate.path("finishReason").asText())) {
        log.warn(
            "PC Gemini response incomplete: maxTokens={}, candidatePresent={}",
            "MAX_TOKENS".equals(candidate.path("finishReason").asText()),
            !candidate.isMissingNode());
        throw invalid();
      }
      var text = new StringBuilder();
      for (var p : candidate.path("content").path("parts"))
        if (!p.path("thought").asBoolean()) text.append(p.path("text").asText(""));
      var parsed = json.readTree(text.toString());
      if (parsed == null || !parsed.isObject()) throw invalid();
      return parsed;
    } catch (AiException e) {
      throw e;
    } catch (java.net.http.HttpTimeoutException e) {
      throw new AiException(GATEWAY_TIMEOUT, "AI 분석이 지연되고 있어요. 잠시 후 다시 시도해주세요.");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AiException(SERVICE_UNAVAILABLE, "AI 분석이 중단됐어요.");
    } catch (java.io.IOException e) {
      if (e instanceof com.fasterxml.jackson.core.JsonProcessingException) throw invalid();
      throw new AiException(SERVICE_UNAVAILABLE, "Gemini 연결에 실패했어요.");
    } catch (Exception e) {
      throw invalid();
    }
  }
}
