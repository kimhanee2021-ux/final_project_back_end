package org.ict.final_project_back_end.domain.gamenews.service;

import org.springframework.web.util.HtmlUtils;

/** Plain display text only. The source is never inserted into HTML or executed. */
public final class OfficialNewsText {
  private OfficialNewsText() {}

  public static String plain(String source) {
    if (source == null || source.isBlank()) return "";
    return HtmlUtils.htmlUnescape(source
        .replaceAll("(?is)<(script|style|iframe)[^>]*>.*?</\\1\\s*>", "")
        .replaceAll("(?is)\\[(img|video|youtube|previewyoutube|previewimg|media)(?:[^\\]]*)].*?\\[/\\1]", "")
        .replaceAll("(?i)<(?:br\\s*/?|/?(?:p|div|h[1-6]|li|tr)[^>]*)>", "\n")
        .replaceAll("(?s)<[^>]*>", "")
        .replaceAll("(?i)\\[\\*]", "\n• ")
        .replaceAll("(?i)\\[/?(?:p|h[1-6]|list|olist|table|tr|hr)(?:=[^\\]]*)?]", "\n")
        .replaceAll("(?i)\\[/?(?:b|i|u|s|strike|url|quote|code|spoiler|td|th|/\\*)(?:[= ][^\\]]*)?]", "")
        .replace("[/*]", "")
        .replace("\\[", "[").replace("\\]", "]"))
        .replace('\u00a0', ' ')
        .replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "")
        .replaceAll("[ \\t]+", " ")
        .replaceAll("•\\s+", "• ")
        .replaceAll(" *\\n *", "\n")
        .replaceAll("\\n{3,}", "\n\n").strip();
  }
}
