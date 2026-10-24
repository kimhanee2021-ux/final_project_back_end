package org.ict.final_project_back_end.domain.gamenews.dto;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.ict.final_project_back_end.domain.gamenews.entity.GameNewsCategory;
import org.ict.final_project_back_end.domain.gamenews.service.OfficialNewsClassifier;
import org.ict.final_project_back_end.domain.gamenews.service.OfficialNewsText;

public record BbsNewsResponse(long appId, Instant fetchedAt, boolean stale, int limit, List<Article> items) {
  public record Article(String id, String title, String content, GameNewsCategory category,
                        Instant publishedAt, String sourceUrl, String feedName) {
    @com.fasterxml.jackson.annotation.JsonProperty("sourceHash")
    public String sourceHash() {
      return org.ict.final_project_back_end.domain.gamenews.service.BriefingContent.hash(this);
    }
    public static Article from(SteamNewsItem item) {
      return new Article(item.sourceNewsId(), OfficialNewsText.plain(item.title()),
          OfficialNewsText.plain(item.content()), OfficialNewsClassifier.classify(item),
          item.publishedAt().toInstant(ZoneOffset.UTC), item.sourceUrl(), SteamNewsItem.OFFICIAL_FEED);
    }
  }
}
