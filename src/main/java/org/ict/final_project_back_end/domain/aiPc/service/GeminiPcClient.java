package org.ict.final_project_back_end.domain.aiPc.service;

import static org.ict.final_project_back_end.domain.aiPc.service.PcAiPrompts.*;
import static org.springframework.http.HttpStatus.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.ict.final_project_back_end.domain.aiCommon.exception.AiException;
import org.ict.final_project_back_end.domain.aiCommon.service.GeminiSupport;
import org.ict.final_project_back_end.domain.aiPc.dto.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** PC AI 기능의 진입점. 설정에 따라 Gemini/Groq 중 공급자를 고르고, 지시문을 조립해 받은 답을 검증한다. */
@Component
public class GeminiPcClient extends GeminiSupport {
  private final GeminiPcTransport geminiTransport;
  private final GroqPcTransport groq;
  private final String provider;

  public GeminiPcClient(String key, String model, String base) {
    super(key, model, base);
    this.geminiTransport = new GeminiPcTransport(key, model, base);
    this.groq = null;
    this.provider = "gemini";
  }

  @Autowired
  public GeminiPcClient(
      @Value("${app.ai.api-key:}") String key,
      @Value("${app.ai.pc.model:gemini-3.5-flash-lite}") String model,
      @Value("${app.ai.base-url:https://generativelanguage.googleapis.com}") String base,
      GroqPcTransport groq,
      @Value("${app.ai.pc.provider:auto}") String provider) {
    super(key, model, base);
    this.geminiTransport = new GeminiPcTransport(key, model, base);
    this.groq = groq;
    this.provider = provider;
  }

  private final ThreadLocal<String> lastProvider = new ThreadLocal<>();

  public String actualProvider() {
    return Objects.toString(lastProvider.get(), "");
  }

  public String provider() {
    if ("groq".equals(provider)) return "Groq";
    if (!Set.of("auto", "gemini", "gemini-first").contains(provider))
      throw new AiException(SERVICE_UNAVAILABLE, "PC AI 공급자 설정을 확인해주세요.");
    return groq != null && groq.configured()
        ? ("auto".equals(provider) ? "Groq ↔ Gemini" : "Gemini → Groq")
        : "Gemini";
  }

  private JsonNode ask(
      String instructions, List<Map<String, Object>> parts, Map<String, Object> schema) {
    return ask(
        instructions,
        parts,
        schema,
        parts.stream().anyMatch(p -> p.containsKey("text")) ? 2400 : 768);
  }

  private JsonNode ask(
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
        return geminiTransport.ask(instructions, parts, schema, outputLimit);
      }
    }
    try {
      lastProvider.set("Gemini");
      return geminiTransport.ask(instructions, parts, schema, outputLimit);
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

  public PcDiagnosis.Extraction extract(byte[] image, String mime) {
    var answer =
        ask(
            EXTRACTION_INSTRUCTIONS,
            List.of(
                Map.of(
                    "inlineData",
                    Map.of("mimeType", mime, "data", Base64.getEncoder().encodeToString(image)))),
            extractionSchema());
    var storage =
        PcStorageReading.resolve(
            rawField(answer, "storageTotalText"),
            rawField(answer, "storageUsedText"),
            rawField(answer, "storageFreeText"));
    var pc =
        new PcSpecs(
            field(answer, "cpuModel"),
            field(answer, "cpuDetails"),
            field(answer, "gpuModel"),
            field(answer, "gpuMemory"),
            number(answer, "ramGB", 4096),
            field(answer, "ramSpeed"),
            storage.total(),
            storage.free(),
            field(answer, "os"));
    var warnings = new ArrayList<>(storage.warnings());
    for (String warning : checkedList(answer.path("warnings")))
      if (!warning.matches(".*(계산|환산|차감).*")) warnings.add(warning);
    return new PcDiagnosis.Extraction(pc, warnings.stream().limit(6).toList(), actualProvider());
  }

  private static String compact(String value, int limit) {
    String text = Objects.toString(value, "");
    return text.length() <= limit
        ? text
        : text.substring(0, limit) + " [자료 일부 생략: 누락 수치는 추측하지 말 것]";
  }

  private String rawField(JsonNode node, String key) {
    return node.has(key) ? checkedString(node.path(key), 80, true) : "";
  }

  private String field(JsonNode node, String key) {
    return checkedString(node.path(key), key.equals("ramSpeed") ? 80 : 160, true);
  }

  private Double number(JsonNode node, String key, int maximum) {
    String value = checkedString(node.path(key), 30, true);
    if (value.isBlank()) return null;
    try {
      double n = Double.parseDouble(value);
      if (!Double.isFinite(n) || n < 0 || n > maximum) throw invalid();
      return n;
    } catch (NumberFormatException e) {
      throw invalid();
    }
  }

  public JsonNode explain(
      PcSpecs pc,
      PcGameRequirements requirements,
      PcRules.Comparison comparison,
      List<PcPart.Offer> offers) {
    try {
      return applyRuleGrade(
          ask(
              explanationInstructions() + targetInstructions() + componentInstructions(),
              List.of(
                  Map.of(
                      "text",
                      json.writeValueAsString(
                          Map.of(
                              "specs",
                              pc,
                              "gpuIdentity",
                              PcHardwarePolicy.gpuIdentity(pc),
                              "requirements",
                              Map.of(
                                  "name",
                                  requirements.name(),
                                  "minimum",
                                  compact(requirements.minimum(), 2400),
                                  "recommended",
                                  compact(requirements.recommended(), 2400)),
                              "offers",
                              offers,
                              "fpsContext",
                              PcFpsEvidence.explanationContext(pc, requirements.appId()))))),
              explanationSchema(),
              4096),
          comparison);
    } catch (AiException e) {
      log.warn("PC AI explanation failed: status={}", e.getStatus().value());
      throw e;
    } catch (Exception e) {
      log.warn("PC AI explanation preparation failed: {}", e.getClass().getSimpleName());
      throw invalid();
    }
  }

  private JsonNode applyRuleGrade(JsonNode answer, PcRules.Comparison comparison) {
    if (!(answer instanceof com.fasterxml.jackson.databind.node.ObjectNode object)) throw invalid();
    object.put("grade", comparison.grade());
    return answer;
  }

  public JsonNode overview(PcSpecs pc, List<PcGameRequirements> games, List<PcPart.Offer> offers) {
    try {
      var context =
          games.stream()
              .map(
                  g ->
                      Map.of(
                          "appId",
                          g.appId(),
                          "name",
                          g.name(),
                          "minimum",
                          compact(g.minimum(), 320),
                          "recommended",
                          compact(g.recommended(), 320),
                          "windows",
                          g.windows()))
              .toList();
      var fields =
          schema(
              Map.of(
                  "overallGrade",
                  Map.of("type", "string", "enum", List.of("A", "B", "C", "D", "E", "UNKNOWN")),
                  "overallExplanation",
                  Map.of("type", "array", "minItems", 4, "maxItems", 4, "items", string()),
                  "componentAdvice",
                  componentAdviceSchema()));
      return ask(
          OVERVIEW_INSTRUCTIONS + componentInstructions(),
          List.of(
              Map.of(
                  "text",
                  json.writeValueAsString(
                      Map.of(
                          "specs",
                          pc,
                          "gpuIdentity",
                          PcHardwarePolicy.gpuIdentity(pc),
                          "games",
                          context,
                          "offers",
                          offers)))),
          fields,
          3072);
    } catch (AiException e) {
      throw e;
    } catch (Exception e) {
      throw invalid();
    }
  }

  public JsonNode explainBatch(
      PcSpecs pc,
      List<PcGameRequirements> games,
      List<PcRules.Comparison> comparisons,
      List<PcPart.Offer> offers) {
    try {
      var entries = new ArrayList<Map<String, Object>>();
      for (int i = 0; i < games.size(); i++) {
        var game = games.get(i);
        var comparison = comparisons.get(i);
        entries.add(
            Map.of(
                "appId",
                game.appId(),
                "requirements",
                Map.of(
                    "name",
                    game.name(),
                    "minimum",
                    compact(game.minimum(), 2400),
                    "recommended",
                    compact(game.recommended(), 2400)),
                "fpsContext",
                PcFpsEvidence.explanationContext(pc, game.appId())));
      }
      var itemFields =
          new LinkedHashMap<String, Object>(
              (Map<String, Object>) explanationSchema(false).get("properties"));
      itemFields.put("appId", Map.of("type", "integer"));
      var item = schema(itemFields);
      var responseSchema =
          schema(
              Map.of(
                  "results",
                  Map.of(
                      "type",
                      "array",
                      "minItems",
                      games.size(),
                      "maxItems",
                      games.size(),
                      "items",
                      item),
                  "componentAdvice",
                  componentAdviceSchema(),
                  "overallExplanation",
                  Map.of("type", "array", "minItems", 4, "maxItems", 4, "items", string())));
      var parts =
          List.of(
              Map.<String, Object>of(
                  "text",
                  json.writeValueAsString(
                      Map.of(
                          "specs",
                          pc,
                          "gpuIdentity",
                          PcHardwarePolicy.gpuIdentity(pc),
                          "games",
                          entries,
                          "offers",
                          offers.stream()
                              .map(
                                  o ->
                                      Map.of(
                                          "productCode",
                                          String.valueOf(o.part().productCode()),
                                          "name",
                                          o.part().name(),
                                          "status",
                                          o.status(),
                                          "hasPrice",
                                          o.priceKrw() != null && o.priceKrw() > 0))
                              .toList()))));
      var answer =
          ask(
              BATCH_INSTRUCTIONS + targetInstructions() + componentInstructions(),
              parts,
              responseSchema,
              4096);
      for (var result : answer.path("results"))
        for (int i = 0; i < games.size(); i++)
          if (result.path("appId").asLong() == games.get(i).appId())
            applyRuleGrade(result, comparisons.get(i));
      return answer;
    } catch (AiException e) {
      throw e;
    } catch (Exception e) {
      throw invalid();
    }
  }

  public String explanation(JsonNode n, String key, int max) {
    JsonNode value = n.path(key);
    if (!value.isTextual() || value.asText().isBlank() || value.asText().length() > max) {
      log.warn(
          "PC AI text rejected: field={}, cause=TYPE_EMPTY_OR_LENGTH, textual={}, length={},"
              + " max={}",
          key,
          value.isTextual(),
          value.asText("").length(),
          max);
      throw invalid();
    }
    String text = value.asText();
    if (text.matches("(?is).*\\d+\\s*(fps|프레임).*")) {
      log.warn("PC AI text rejected: field={}, cause=NUMERIC_FPS", key);
      throw invalid();
    }
    return text;
  }

  public List<String> list(JsonNode n, String key) {
    List<String> values;
    try {
      values = checkedList(n.path(key));
    } catch (AiException failure) {
      log.warn("PC AI list rejected: field={}, cause=TYPE_COUNT_OR_LENGTH", key);
      throw failure;
    }
    if (values.stream().anyMatch(text -> text.matches("(?is).*\\d+\\s*(fps|프레임).*"))) {
      log.warn("PC AI list rejected: field={}, cause=NUMERIC_FPS", key);
      throw invalid();
    }
    return values;
  }
}
