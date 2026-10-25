package org.ict.final_project_back_end.domain.wishsales.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

// Groq (OpenAI 호환 Chat Completions API, api.groq.com). GROQ_API_KEY, GROQ_MODEL
@Component
public class GroqAdviceTextProvider implements AdviceTextProvider {

  private final RestClient http = RestClient.create();
  private final ObjectMapper mapper = new ObjectMapper();
  private final String key;
  private final String model;

  public GroqAdviceTextProvider(@Value("${GROQ_API_KEY:}") String key,
                                @Value("${GROQ_MODEL:llama-3.3-70b-versatile}") String model) {
    this.key = key;
    this.model = model;
  }

  @Override
  public String name() {
    return "groq";
  }

  @Override
  public boolean configured() {
    return key != null && !key.isBlank();
  }

  @Override
  public String complete(String prompt) {
    ObjectNode body = mapper.createObjectNode();
    body.put("model", model);
    body.putArray("messages").addObject().put("role", "user").put("content", prompt);
    JsonNode res = http.post()
        .uri("https://api.groq.com/openai/v1/chat/completions")
        .header("Authorization", "Bearer " + key)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .body(JsonNode.class);
    return res.path("choices").get(0).path("message").path("content").asText();
  }
}
