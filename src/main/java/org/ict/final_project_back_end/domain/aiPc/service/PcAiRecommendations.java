package org.ict.final_project_back_end.domain.aiPc.service;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.ict.final_project_back_end.domain.aiCommon.exception.AiException;
import org.ict.final_project_back_end.domain.aiPc.dto.*;

/** AI가 선택한 상품을 실제 수집 후보와 결합한다. 가격/출처는 AI가 생성하지 않는다. */
public final class PcAiRecommendations {
  private PcAiRecommendations() {}

  public static List<PcDiagnosis.UpgradeRecommendation> parse(
      JsonNode answer, List<PcPart.Offer> offers) {
    var nodes = answer.path("recommendations");
    if (!nodes.isArray() || nodes.size() > 3) throw invalid();
    var result = new ArrayList<PcDiagnosis.UpgradeRecommendation>();
    var seen = new HashSet<Long>();
    for (var node : nodes) {
      String code = text(node, "productCode", 20);
      if (!code.matches("[0-9]{1,18}")) throw invalid();
      long id = Long.parseLong(code);
      if (!seen.add(id)) throw invalid();
      var offer =
          offers.stream()
              .filter(
                  o ->
                      o.part().productCode() == id
                          && "OBSERVED".equals(o.status())
                          && o.priceKrw() != null
                          && o.priceKrw() > 0)
              .findFirst()
              .orElseThrow(PcAiRecommendations::invalid);
      result.add(
          new PcDiagnosis.UpgradeRecommendation(
              offer, text(node, "reason", 500), text(node, "compatibilityNote", 500)));
    }
    return List.copyOf(result);
  }

  private static String text(JsonNode node, String field, int max) {
    var value = node.path(field);
    if (!value.isTextual()
        || value.asText().isBlank()
        || value.asText().length() > max
        || value.asText().matches("(?is).*\\d+\\s*(fps|프레임).*")) throw invalid();
    return value.asText();
  }

  private static AiException invalid() {
    return new AiException(BAD_GATEWAY, "AI 업그레이드 추천의 근거를 확인하지 못했어요.");
  }
}
