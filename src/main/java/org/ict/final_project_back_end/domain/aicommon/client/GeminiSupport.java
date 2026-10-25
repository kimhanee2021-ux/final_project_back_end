package org.ict.final_project_back_end.domain.aicommon.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.ict.final_project_back_end.domain.aicommon.exception.AiException;

/** Gemini generateContent를 직접 부르는 클라이언트의 공통 설정(키·모델·주소, HTTP 클라이언트)과 답 검증. */
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
    return AiAnswerChecks.checkedString(node, max, empty);
  }

  protected List<String> checkedList(JsonNode node) {
    return AiAnswerChecks.checkedList(node);
  }

  protected AiException invalid() {
    return AiAnswerChecks.invalid();
  }
}
