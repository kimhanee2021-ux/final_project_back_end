package org.ict.final_project_back_end.domain.gamenews.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.*;
import java.util.*;
import org.ict.final_project_back_end.domain.gamenews.dto.BbsNewsResponse.Article;
import org.ict.final_project_back_end.domain.gamenews.exception.BbsBriefingException;

/** Source snapshots are data, never instructions. Timing is an estimate until actual TTS is connected. */
public final class BriefingContent {
  private BriefingContent() {}
  public static final String PROMPT_VERSION = "bbs-ko-2";
  public static final int MAX_SOURCE = 24_000;
  private static final ObjectMapper JSON = new ObjectMapper();
  public record Draft(String titleKo, List<String> points, String narrationScript) {}
  public record Source(String title, String content, String sourceUrl, String publishedAt, String category) {}
  public record Payload(Draft draft, Source source) {}

  public static String hash(Article article) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
        JSON.writeValueAsBytes(List.of(article.id(), article.title(), article.content(),
            article.sourceUrl(), article.publishedAt().toString(), article.category().name())))); }
    catch (Exception ex) { throw new IllegalStateException("Cannot fingerprint official source", ex); }
  }
  public static void validateSource(Article article) {
    if (article.content().replaceAll("\\s", "").length() < 80) throw BbsBriefingException.insufficient();
    // Never silently summarize a truncated long patch note as if it were the whole source.
    if (article.content().length() > MAX_SOURCE) throw new BbsBriefingException(
        org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "BBS_BRIEFING_SOURCE_TOO_LONG",
        "이 소식은 본문이 길어 현재 요약 범위를 넘어요. 공식 원문에서 확인해 주세요.");
  }
  public static Draft validate(Draft draft) {
    if (draft == null || !korean(draft.titleKo(), 3, 200) || draft.points() == null || draft.points().size() != 3
        || draft.points().stream().anyMatch(p -> !korean(p, 8, 240))
        || new HashSet<>(draft.points().stream().map(String::strip).toList()).size() != 3
        || !korean(draft.narrationScript(), 240, 520)) throw BbsBriefingException.unavailable();
    return new Draft(draft.titleKo().strip(), draft.points().stream().map(String::strip).toList(), draft.narrationScript().strip());
  }
  private static boolean korean(String text, int min, int max) {
    if (text == null) return false;
    int length = text.strip().length();
    return length >= min && length <= max && text.codePoints().filter(c -> c >= 0xac00 && c <= 0xd7a3).count() >= 3
        && !text.matches("(?s).*[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F].*")
        && !text.contains("<") && !text.contains(">") && !text.contains("http://") && !text.contains("https://");
  }
  public static int seconds(String script) { return Math.max(30, Math.min(60, (int)Math.round(script.length() / 8.5))); }
  public static String encode(Draft draft, Article article) {
    try { return JSON.writeValueAsString(new Payload(validate(draft), new Source(article.title(), article.content(),
        article.sourceUrl(), article.publishedAt().toString(), article.category().name()))); }
    catch (java.io.IOException ex) { throw BbsBriefingException.storage(); }
  }
  public static Draft decode(String payload) {
    try { return validate(JSON.readValue(payload, Payload.class).draft()); }
    catch (Exception ex) { throw BbsBriefingException.storage(); }
  }
}
