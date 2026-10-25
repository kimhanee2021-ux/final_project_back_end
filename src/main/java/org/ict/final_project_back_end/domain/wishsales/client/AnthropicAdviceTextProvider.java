package org.ict.final_project_back_end.domain.wishsales.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

// Claude (Anthropic Messages API, api.anthropic.com). ANTHROPIC_API_KEY, ANTHROPIC_MODEL
@Component
public class AnthropicAdviceTextProvider implements AdviceTextProvider {

  private final RestClient http = RestClient.create();
  private final ObjectMapper mapper = new ObjectMapper();
  private final String key;
  private final String model;

  public AnthropicAdviceTextProvider(@Value("${ANTHROPIC_API_KEY:}") String key,
                                     @Value("${ANTHROPIC_MODEL:claude-haiku-5-5}") String model) {
    this.key = key;
    this.model = model;
  }

  @Override
  public String name() {
    return "anthropic";
  }

  @Override
  public boolean configured() {
    return key != null && !key.isBlank();
  }

  @Override
  public String complete(String prompt) {
    ObjectNode body = mapper.createObjectNode();
    body.put("model", model);
    body.put("max_tokens", 2000);
    body.putArray("messages").addObject().put("role", "user").put("content", prompt);
    JsonNode res = http.post()
        .uri("https://api.anthropic.com/v1/messages")
        .header("x-api-key", key)
        .header("anthropic-version", "2023-06-01")
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .body(JsonNode.class);
    return res.path("content").get(0).path("text").asText();
  }
}
