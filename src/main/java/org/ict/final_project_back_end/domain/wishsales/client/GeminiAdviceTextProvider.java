package org.ict.final_project_back_end.domain.wishsales.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

// Gemini (Google Generative Language API). GEMINI_API_KEY, GEMINI_MODEL. 키는 URL이 아니라 헤더로 보낸다.
@Component
public class GeminiAdviceTextProvider implements AdviceTextProvider {

  private final RestClient http = RestClient.create();
  private final ObjectMapper mapper = new ObjectMapper();
  private final String key;
  private final String model;

  public GeminiAdviceTextProvider(@Value("${GEMINI_API_KEY:}") String key,
                                  @Value("${GEMINI_MODEL:gemini-3.5-flash-lite}") String model) {
    this.key = key;
    this.model = model;
  }

  @Override
  public String name() {
    return "gemini";
  }

  @Override
  public boolean configured() {
    return key != null && !key.isBlank();
  }

  @Override
  public String complete(String prompt) {
    ObjectNode body = mapper.createObjectNode();
    body.putArray("contents").addObject().putArray("parts").addObject().put("text", prompt);
    JsonNode res = http.post()
        .uri("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent")
        .header("x-goog-api-key", key)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .body(JsonNode.class);
    return res.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();
  }
}
