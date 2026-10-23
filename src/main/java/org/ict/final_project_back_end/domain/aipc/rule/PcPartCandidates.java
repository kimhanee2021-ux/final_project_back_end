package org.ict.final_project_back_end.domain.aipc.rule;

import java.util.*;
import org.ict.final_project_back_end.domain.aipc.dto.PcPart;

/** Known product identities remain available even when no current price was observed. */
public final class PcPartCandidates {
  private PcPartCandidates() {}

  public static List<PcPart.Offer> includeUnpriced(List<PcPart.Offer> observed) {
    var values = new LinkedHashMap<Long, PcPart.Offer>();
    if (observed != null) for (var offer : observed) values.put(offer.part().productCode(), offer);
    for (var part : PcPartsCatalog.PARTS)
      if (Set.of("CPU", "GPU", "RAM", "SSD").contains(part.category()))
        values.putIfAbsent(
            part.productCode(),
            new PcPart.Offer(
                part,
                null,
                "UNVERIFIED",
                "제품 비교 후보이며 현재 가격·장착 호환은 미확인이에요.",
                null,
                null,
                null,
                null));
    return List.copyOf(values.values());
  }
}
