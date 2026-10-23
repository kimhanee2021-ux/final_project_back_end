package org.ict.final_project_back_end.domain.aipc.client;

import java.util.List;
import java.util.Map;

/** 공급자 선택용 보수적 분량 추산. 실제 토큰 계산이나 한도 차단에 사용하지 않는다. */
public final class PcAiRouting {
  private PcAiRouting() {}

  public static boolean preferGroq(
      String instructions,
      List<Map<String, Object>> parts,
      Map<String, Object> schema,
      int outputLimit) {
    if (parts.stream().anyMatch(p -> p.containsKey("inlineData"))) return false;
    if (schema.get("properties") instanceof Map<?, ?> properties
        && properties.containsKey("results")) return false;
    long estimated =
        units(instructions)
            + units(parts.toString())
            + units(schema.toString())
            + outputLimit
            + 512;
    return estimated <= 4500;
  }

  private static long units(String text) {
    long ascii = text.chars().filter(c -> c < 128).count();
    return (ascii + 2) / 3 + (text.length() - ascii) * 2;
  }
}
