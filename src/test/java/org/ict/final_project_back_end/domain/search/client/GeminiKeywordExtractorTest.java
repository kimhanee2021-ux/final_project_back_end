package org.ict.final_project_back_end.domain.search.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Gemini 호출 없이 응답 해석과 프롬프트 구성만 검증한다
class GeminiKeywordExtractorTest {

  private final ObjectMapper json = new ObjectMapper();

  @Test
  void parsesKeywordsFromAGeminiResponse() throws Exception {
    byte[] body = geminiResponse("STOP", Map.of(
        "tags", List.of("Farming Sim", "Cozy"), "exclude_tags", List.of("Horror"),
        "description", "a cozy farming life game", "similar_to", "Stardew Valley"));

    SearchKeywords keywords = GeminiKeywordExtractor.parse(json, body);

    assertEquals(List.of("Farming Sim", "Cozy"), keywords.tags());
    assertEquals(List.of("Horror"), keywords.excludeTags());
    assertEquals("a cozy farming life game", keywords.description());
    assertEquals("Stardew Valley", keywords.similarTo());
  }

  @Test
  void missingOptionalFieldsBecomeEmpty() throws Exception {
    SearchKeywords keywords = GeminiKeywordExtractor.parse(json, geminiResponse("STOP", Map.of("description", "a zombie game")));

    assertEquals(List.of(), keywords.tags());
    assertEquals("", keywords.similarTo());
    assertTrue(keywords.hasPositive());
  }

  @Test
  void ignoresThoughtPartsOfThinkingModels() throws Exception {
    byte[] body = json.writeValueAsBytes(Map.of("candidates", List.of(Map.of("finishReason", "STOP", "content", Map.of("parts", List.of(
        Map.of("thought", true, "text", "생각 중..."),
        Map.of("text", "{\"description\": \"a rhythm game\"}")))))));

    assertEquals("a rhythm game", GeminiKeywordExtractor.parse(json, body).description());
  }

  @Test
  void rejectsAnUnfinishedResponse() throws Exception {
    byte[] body = geminiResponse("MAX_TOKENS", Map.of("description", "cut off"));

    assertThrows(KeywordExtractionException.class, () -> GeminiKeywordExtractor.parse(json, body));
  }

  @Test
  void rejectsMalformedAnswers() throws Exception {
    assertThrows(KeywordExtractionException.class,
        () -> GeminiKeywordExtractor.parse(json, geminiResponse("STOP", Map.of("tags", "Farming Sim")))); // 목록이 아님
    assertThrows(KeywordExtractionException.class,
        () -> GeminiKeywordExtractor.parse(json, geminiResponse("STOP", Map.of("tags", List.of("a", "b", "c", "d", "e", "f"))))); // 5개 초과
    assertThrows(KeywordExtractionException.class,
        () -> GeminiKeywordExtractor.parse(json, geminiResponse("STOP", Map.of("description", "x".repeat(501)))));
    assertThrows(KeywordExtractionException.class, () -> GeminiKeywordExtractor.parse(json, "not json".getBytes()));
    assertThrows(KeywordExtractionException.class, () -> GeminiKeywordExtractor.parse(json, "{}".getBytes()));
  }

  @Test
  void promptListsTheKnownTagsAndNothingIsLeftUnfilled() {
    GeminiKeywordExtractor extractor = extractor("key");

    assertTrue(extractor.systemPrompt().contains("Souls-like"));
    assertTrue(extractor.systemPrompt().contains("Farming Sim"));
    assertFalse(extractor.systemPrompt().contains("{{TAGS}}"));
  }

  @Test
  void sendsTheQueryAsTheUserMessageAndTheRulesAsTheSystemInstruction() {
    Map<String, Object> body = extractor("key").requestBody("농사 게임");

    assertTrue(body.toString().contains("농사 게임"));
    assertTrue(body.get("systemInstruction").toString().contains("Steam"));
  }

  @Test
  void withoutAnApiKeyItFailsWithoutCallingGemini() {
    assertThrows(KeywordExtractionException.class, () -> extractor("").extract("농사 게임"));
  }

  private GeminiKeywordExtractor extractor(String apiKey) {
    return new GeminiKeywordExtractor(apiKey, "gemini-flash-latest", "http://localhost:1", 1);
  }

  private byte[] geminiResponse(String finishReason, Map<String, Object> answer) throws Exception {
    return json.writeValueAsBytes(Map.of("candidates", List.of(Map.of(
        "finishReason", finishReason,
        "content", Map.of("parts", List.of(Map.of("text", json.writeValueAsString(answer))))))));
  }
}
