package org.ict.final_project_back_end.domain.gamenews.service;

import java.util.Locale;
import java.util.regex.Pattern;
import org.ict.final_project_back_end.domain.gamenews.dto.SteamNewsItem;
import org.ict.final_project_back_end.domain.gamenews.entity.GameNewsCategory;

/** These are GameSense display groups, not event types guaranteed by ISteamNews. */
public final class OfficialNewsClassifier {
  private static final Pattern DEV = Pattern.compile(
      "\\b(dev(?:eloper)?[ -]?(?:blog|log|diary|update)|development|roadmap|behind the scenes)\\b|개발[ 자일]*소식|개발자|개발 일지|로드맵", Pattern.CASE_INSENSITIVE);
  private static final Pattern PATCH = Pattern.compile(
      "\\b(patch(?:[ -]?notes)?|hotfix|changelog|release notes|update(?:d|s)?)\\b|패치|핫픽스|업데이트|수정 사항", Pattern.CASE_INSENSITIVE);
  private static final Pattern EVENT = Pattern.compile(
      "\\b(event|tournament|championship|festival|anniversary|livestream|playtest|qualifier|finals?|major)\\b|이벤트|대회|축제|주년|챔피언십|결승|생방송", Pattern.CASE_INSENSITIVE);

  private OfficialNewsClassifier() {}

  public static GameNewsCategory classify(SteamNewsItem item) {
    var tags = item.tags().stream().map(tag -> tag.toLowerCase(Locale.ROOT)).toList();
    if (tags.contains("patchnotes")) return GameNewsCategory.PATCH;
    String title = OfficialNewsText.plain(item.title());
    if (DEV.matcher(title).find()) return GameNewsCategory.DEV_NEWS;
    if (PATCH.matcher(title).find()) return GameNewsCategory.PATCH;
    if (EVENT.matcher(title).find()) return GameNewsCategory.EVENT;
    return GameNewsCategory.NOTICE;
  }
}
