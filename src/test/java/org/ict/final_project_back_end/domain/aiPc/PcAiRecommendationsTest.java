package org.ict.final_project_back_end.domain.aiPc;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.ict.final_project_back_end.domain.aiCommon.exception.AiException;
import org.ict.final_project_back_end.domain.aiPc.dto.PcPart;
import org.ict.final_project_back_end.domain.aiPc.service.PcAiRecommendations;
import org.junit.jupiter.api.Test;

class PcAiRecommendationsTest {
  private final ObjectMapper json = new ObjectMapper();

  private PcPart.Offer offer(String status, Long price) {
    return new PcPart.Offer(
        new PcPart("GPU", "확인한 후보", 123, "https://example.invalid/spec", List.of("전원 확인")),
        price,
        status,
        "수집값",
        "2026-10-19T00:00:00Z",
        null,
        null,
        null);
  }

  private com.fasterxml.jackson.databind.JsonNode answer(String code) throws Exception {
    return json.readTree(
        "{\"recommendations\":[{\"productCode\":\""
            + code
            + "\",\"reason\":\"그래픽 요구 사항 확인 후 검토\",\"compatibilityNote\":\"전원과 공간 확인"
            + " 필요\",\"priceKrw\":1}]}");
  }

  @Test
  void usesObservedPriceAndSourceInsteadOfAiInventedPrice() throws Exception {
    var result = PcAiRecommendations.parse(answer("123"), List.of(offer("OBSERVED", 200000L)));
    assertEquals(200000L, result.getFirst().offer().priceKrw());
    assertEquals("확인한 후보", result.getFirst().offer().part().name());
  }

  @Test
  void rejectsUnknownOrUnavailableProducts() throws Exception {
    assertThrows(
        AiException.class,
        () -> PcAiRecommendations.parse(answer("999"), List.of(offer("OBSERVED", 200000L))));
    assertThrows(
        AiException.class,
        () -> PcAiRecommendations.parse(answer("123"), List.of(offer("UNAVAILABLE", null))));
  }

  @Test
  void allowsNoPurchaseRecommendationButRejectsMissingAnswer() throws Exception {
    assertTrue(
        PcAiRecommendations.parse(json.readTree("{\"recommendations\":[]}"), List.of()).isEmpty());
    assertThrows(
        AiException.class, () -> PcAiRecommendations.parse(json.readTree("{}"), List.of()));
  }

  @Test
  void rejectsDuplicateSelections() throws Exception {
    var data = answer("123");
    ((com.fasterxml.jackson.databind.node.ArrayNode) data.path("recommendations"))
        .add(data.path("recommendations").get(0).deepCopy());
    assertThrows(
        AiException.class,
        () -> PcAiRecommendations.parse(data, List.of(offer("OBSERVED", 200000L))));
  }
}
