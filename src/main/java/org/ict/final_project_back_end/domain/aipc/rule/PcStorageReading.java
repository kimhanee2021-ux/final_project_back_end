package org.ict.final_project_back_end.domain.aipc.rule;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;
import org.ict.final_project_back_end.domain.aicommon.exception.AiException;

/** AI는 원문 숫자와 단위만 읽고, 단위 환산과 남은 공간 계산은 서버가 수행한다. */
public final class PcStorageReading {
  private PcStorageReading() {}

  public record Result(Double total, Double free, List<String> warnings) {}

  public static Result resolve(String totalText, String usedText, String freeText) {
    Double total = totalText.isBlank() ? null : amount(totalText),
        free = freeText.isBlank() ? null : amount(freeText),
        used = usedText.isBlank() ? null : amount(usedText);
    var warnings = new ArrayList<String>();
    if (total != null && used != null) {
      double calculated =
          BigDecimal.valueOf(total).subtract(BigDecimal.valueOf(used)).doubleValue();
      if (calculated < 0
          || (free != null && !freeText.isBlank() && Math.abs(free - calculated) > 0.05)) {
        free = null;
        warnings.add("저장공간 수치가 일치하지 않아 남은 공간을 확정하지 않았어요. 사진의 총량·사용량·여유 공간을 확인해 주세요.");
      } else {
        free = calculated;
        warnings.add("남은 공간은 사진의 총 저장공간에서 사용량을 서버가 차감해 계산했어요.");
      }
    }
    if (total != null && free != null && free > total) {
      free = null;
      warnings.add("남은 공간이 총량보다 크게 읽혀 확인이 필요해요.");
    }
    return new Result(total, free, List.copyOf(warnings));
  }

  private static Double amount(String text) {
    var match = Pattern.compile("(?i)^([0-9]+(?:\\.[0-9]+)?)\\s*(TB|GB|MB)$").matcher(text.trim());
    if (!match.matches()) throw invalid();
    var value = new BigDecimal(match.group(1));
    value =
        switch (match.group(2).toUpperCase(Locale.ROOT)) {
          case "TB" -> value.multiply(BigDecimal.valueOf(1024));
          case "MB" -> value.divide(BigDecimal.valueOf(1024));
          default -> value;
        };
    if (value.signum() < 0 || value.compareTo(BigDecimal.valueOf(100000)) > 0) throw invalid();
    return value.doubleValue();
  }

  private static AiException invalid() {
    return new AiException(BAD_GATEWAY, "저장공간의 숫자와 단위를 확인하지 못했어요.");
  }
}
