package org.ict.final_project_back_end.domain.aipc.client;

import java.util.Locale;

/** 공급자 원문/조직ID/키를 노출하지 않고 제한 종류와 숫자만 안내한다. */
public final class GroqPcQuota {
  private GroqPcQuota() {}

  public static String describe(String message) {
    String lower = message.toLowerCase(Locale.ROOT);
    String kind =
        lower.contains("tpd") || lower.contains("tokens per day")
            ? "일일 토큰"
            : lower.contains("rpd") || lower.contains("requests per day")
                ? "일일 요청"
                : lower.contains("tpm") || lower.contains("tokens per minute")
                    ? "분당 토큰"
                    : lower.contains("rpm") || lower.contains("requests per minute")
                        ? "분당 요청"
                        : "이용";
    StringBuilder result = new StringBuilder("Groq ").append(kind).append(" 제한으로 분석을 중단했어요.");
    for (String field : new String[] {"Limit", "Used", "Requested"}) {
      var m =
          java.util.regex.Pattern.compile("(?i)\\b" + field + "\\s*:?\\s*([0-9]+(?:\\.[0-9]+)?)")
              .matcher(message);
      if (m.find())
        result
            .append(" ")
            .append(
                switch (field) {
                  case "Limit" -> "한도";
                  case "Used" -> "사용";
                  default -> "이번 요청";
                })
            .append(" ")
            .append(m.group(1));
    }
    return result
        .append(
            kind.startsWith("분당")
                ? ". 하루 한도 소진과는 달라요. 사진을 다시 올리지 말고 제한 회복 후 기존 사양으로 분석해 주세요."
                : ". 사진 재업로드로 제한이 해제되지는 않아요.")
        .toString();
  }
}
