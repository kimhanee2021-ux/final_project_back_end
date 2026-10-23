package org.ict.final_project_back_end.domain.aipc.rule;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.ict.final_project_back_end.domain.aipc.dto.*;

public final class PcComponentAdviceReader {
  public static final List<String> CATEGORIES = List.of("CPU", "GPU", "RAM", "SSD");

  private PcComponentAdviceReader() {}

  public static List<PcComponentAdvice> parse(JsonNode nodes, List<PcPart.Offer> offers) {
    var results = new ArrayList<PcComponentAdvice>();
    for (String category : CATEGORIES) {
      var found = new ArrayList<JsonNode>();
      if (nodes.isArray())
        for (var node : nodes) if (category.equals(node.path("category").asText())) found.add(node);
      String status = "CHECK", reason = "이 부품의 AI 의견이 확인되지 않았어요.", note = "모델명과 장착 조건을 확인해 주세요.";
      PcPart.Offer selected = null;
      if (found.size() == 1) {
        var node = found.getFirst();
        String proposed = node.path("status").asText();
        String r = node.path("reason").asText().trim(),
            n = node.path("compatibilityNote").asText().trim();
        if (Set.of("UPGRADE", "KEEP", "CHECK").contains(proposed)
            && !r.isEmpty()
            && r.length() <= 240
            && !n.isEmpty()
            && n.length() <= 240) {
          status = proposed;
          reason = r;
          note = n;
          // 사진에 없는 보드/전원/장착 정보를 AI가 확인한 것처럼 보장하지 않는다.
          if (Set.of("메인보드", "파워", "쿨러", "케이스").contains(category)) status = "CHECK";
          if ("UPGRADE".equals(status)) {
            String code = node.path("productCode").asText();
            selected =
                offers.stream()
                    .filter(
                        o ->
                            category.equals(o.part().category())
                                && code.equals(String.valueOf(o.part().productCode())))
                    .findFirst()
                    .orElse(null);
          }
        }
      }
      results.add(new PcComponentAdvice(category, status, reason, note, selected));
    }
    return List.copyOf(results);
  }
}
