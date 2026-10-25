package org.ict.final_project_back_end.domain.aipc.client;

import static org.springframework.http.HttpStatus.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.ict.final_project_back_end.domain.aicommon.exception.AiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * PC AI 요청을 어느 공급자(Gemini/Groq)에 보낼지 고르고, 한도 초과·혼잡이면 다른 공급자로 한 번 넘긴다.
 * app.ai.pc.provider: groq(Groq만) · gemini/gemini-first(Gemini 먼저, Groq 예비) · auto(짧은 글은 Groq 먼저, 나머지는 Gemini 먼저).
 * 사진(inlineData)이 든 요청은 Gemini가 먼저 맡고, Gemini가 성공하면 사진을 Groq로 보내지 않는다.
 */
@Component
public class PcAiProviderRouter {
  private static final Logger log = LoggerFactory.getLogger(PcAiProviderRouter.class);

  private final GeminiPcTransport gemini;
  private final GroqPcTransport groq;
  private final String provider;
  // 이번 요청에 실제로 답한 공급자 (화면에 "Groq로 분석" 등으로 보여준다)
  private final ThreadLocal<String> lastProvider = new ThreadLocal<>();

  // Gemini만 쓰는 구성 (테스트용)
  public PcAiProviderRouter(String key, String model, String base) {
    this.gemini = new GeminiPcTransport(key, model, base);
    this.groq = null;
    this.provider = "gemini";
  }

  @Autowired
  public PcAiProviderRouter(
      @Value("${app.ai.api-key:}") String key,
      @Value("${app.ai.pc.model:gemini-3.5-flash-lite}") String model,
      @Value("${app.ai.base-url:https://generativelanguage.googleapis.com}") String base,
      GroqPcTransport groq,
      @Value("${app.ai.pc.provider:auto}") String provider) {
    this.gemini = new GeminiPcTransport(key, model, base);
    this.groq = groq;
    this.provider = provider;
  }

  public String actualProvider() {
    return Objects.toString(lastProvider.get(), "");
  }

  // 화면에 보여줄 공급자 구성 이름
  public String provider() {
    if ("groq".equals(provider)) return "Groq";
    if (!Set.of("auto", "gemini", "gemini-first").contains(provider))
      throw new AiException(SERVICE_UNAVAILABLE, "PC AI 공급자 설정을 확인해주세요.");
    return groq != null && groq.configured()
        ? ("auto".equals(provider) ? "Groq ↔ Gemini" : "Gemini → Groq")
        : "Gemini";
  }

  // 출력 한도를 따로 정하지 않은 요청: 글 자료가 있으면 2400, 사진만 있으면 768 토큰
  public JsonNode ask(
      String instructions, List<Map<String, Object>> parts, Map<String, Object> schema) {
    return ask(
        instructions,
        parts,
        schema,
        parts.stream().anyMatch(p -> p.containsKey("text")) ? 2400 : 768);
  }

  public JsonNode ask(
      String instructions,
      List<Map<String, Object>> parts,
      Map<String, Object> schema,
      int outputLimit) {
    lastProvider.remove();
    String route = provider();
    if ("Groq".equals(route)) {
      lastProvider.set("Groq");
      return parts.stream().anyMatch(p -> p.containsKey("inlineData"))
          ? groq.ask(instructions, parts, schema)
          : groq.ask(instructions, parts, schema, outputLimit);
    }
    if ("Groq ↔ Gemini".equals(route)
        && PcAiRouting.preferGroq(instructions, parts, schema, outputLimit)) {
      try {
        lastProvider.set("Groq");
        return groq.ask(instructions, parts, schema, outputLimit);
      } catch (AiException failure) {
        if (!Set.of(TOO_MANY_REQUESTS, SERVICE_UNAVAILABLE).contains(failure.getStatus()))
          throw failure;
        log.warn(
            "PC AI fallback: Groq status={} -> Gemini (one attempt)", failure.getStatus().value());
        lastProvider.set("Gemini");
        return gemini.ask(instructions, parts, schema, outputLimit);
      }
    }
    try {
      lastProvider.set("Gemini");
      return gemini.ask(instructions, parts, schema, outputLimit);
    } catch (AiException failure) {
      if (Set.of("Gemini → Groq", "Groq ↔ Gemini").contains(route)
          && Set.of(TOO_MANY_REQUESTS, SERVICE_UNAVAILABLE).contains(failure.getStatus())) {
        log.warn(
            "PC AI fallback: Gemini status={} -> Groq (one attempt)", failure.getStatus().value());
        lastProvider.set("Groq");
        return parts.stream().anyMatch(p -> p.containsKey("inlineData"))
            ? groq.ask(instructions, parts, schema)
            : groq.ask(instructions, parts, schema, outputLimit);
      }
      throw failure;
    }
  }
}
