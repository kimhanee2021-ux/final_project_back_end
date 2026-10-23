package org.ict.final_project_back_end.domain.gamenews.client;

import org.ict.final_project_back_end.domain.gamenews.exception.BriefingGenerationException;

import org.ict.final_project_back_end.domain.gamenews.entity.GameNewsCategory;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.regex.Pattern;

public interface NewsBriefingAiClient {
  String modelName();
  String promptVersion();
  Draft generate(Source source);

  record Source(long newsId, long appId, String gameName, String title, String content,
                String sourceUrl, LocalDateTime publishedAt, String contentHash) { }

  record Draft(GameNewsCategory category, String summary, String aiContent) {
    private static final Pattern KOREAN = Pattern.compile("[가-힣]");
    public Draft {
      Objects.requireNonNull(category, "category");
      summary = checked(summary, 500);
      aiContent = checked(aiContent, 6000);
    }
    private static String checked(String value, int max) {
      if (value == null || value.isBlank() || value.length() > max || !KOREAN.matcher(value).find()
          || value.contains("<") || value.contains(">") || value.contains("\u0000")) {
        throw new BriefingGenerationException("AI_INVALID_RESPONSE");
      }
      return value.strip();
    }
  }
}
