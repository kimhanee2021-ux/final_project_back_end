package org.ict.final_project_back_end.domain.aicommon.client;

import static org.springframework.http.HttpStatus.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.ict.final_project_back_end.domain.aicommon.exception.AiException;

public abstract class GeminiSupport {
  protected final String key, model, baseUrl;
  protected final ObjectMapper json = new ObjectMapper();
  protected final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  protected final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(getClass());

  protected GeminiSupport(String key, String model, String baseUrl) {
    this.key = key;
    this.model = model;
    this.baseUrl = baseUrl;
  }

  protected String checkedString(JsonNode node, int max, boolean empty) {
    if (!node.isTextual() || node.asText().length() > max || (!empty && node.asText().isBlank()))
      throw invalid();
    return node.asText();
  }

  protected List<String> checkedList(JsonNode node) {
    if (!node.isArray() || node.size() > 6) throw invalid();
    List<String> result = new ArrayList<>();
    for (var item : node) result.add(checkedString(item, 500, false));
    return List.copyOf(result);
  }

  protected AiException invalid() {
    return new AiException(BAD_GATEWAY, "AI 답변을 확인하지 못했습니다. 질문을 바꾸거나 다시 시도해주세요.");
  }
}
