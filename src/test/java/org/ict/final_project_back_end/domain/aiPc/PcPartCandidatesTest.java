package org.ict.final_project_back_end.domain.aiPc;

import static org.junit.jupiter.api.Assertions.*;

import org.ict.final_project_back_end.domain.aiPc.service.PcPartCandidates;
import org.junit.jupiter.api.Test;

class PcPartCandidatesTest {
  @Test
  void emptyPriceCacheDoesNotEraseKnownProductModelsOrInventPrices() {
    var candidates = PcPartCandidates.includeUnpriced(java.util.List.of());
    assertTrue(candidates.stream().anyMatch(o -> o.part().category().equals("GPU")));
    assertTrue(
        candidates.stream().allMatch(o -> o.priceKrw() == null && o.status().equals("UNVERIFIED")));
  }
}
