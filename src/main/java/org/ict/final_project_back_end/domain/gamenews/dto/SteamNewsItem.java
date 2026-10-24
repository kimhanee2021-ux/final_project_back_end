package org.ict.final_project_back_end.domain.gamenews.dto;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Objects;
import java.util.List;
import java.util.Set;

/** Validated official source; publishedAt is UTC. Keep HTML/BBCode as original text. */
public record SteamNewsItem(long appId, String sourceNewsId, String title, String content,
                            String sourceUrl, LocalDateTime publishedAt, List<String> tags) {
  public static final String OFFICIAL_FEED = "steam_community_announcements";

  public SteamNewsItem {
    if (appId <= 0 || appId > 0xffffffffL) throw new IllegalArgumentException("Invalid Steam app ID");
    requireText(sourceNewsId, 64, "gid");
    if (!sourceNewsId.matches("[0-9]+")) throw new IllegalArgumentException("Invalid Steam gid");
    requireText(title, 1000, "title");
    Objects.requireNonNull(content, "content");
    if (content.length() > 200_000) throw new IllegalArgumentException("Oversized content");
    tags = List.copyOf(tags);
    requireText(sourceUrl, 2048, "url");
    URI uri = URI.create(sourceUrl);
    if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
        || uri.getHost() == null || uri.getUserInfo() != null || uri.getPort() != -1
        || !Set.of("steamcommunity.com", "store.steampowered.com", "steamstore-a.akamaihd.net")
            .contains(uri.getHost().toLowerCase(java.util.Locale.ROOT))) {
      throw new IllegalArgumentException("Invalid source URL");
    }
    if ("http".equalsIgnoreCase(uri.getScheme())) sourceUrl = "https" + sourceUrl.substring(4);
    Objects.requireNonNull(publishedAt, "publishedAt");
    if (publishedAt.getYear() < 1970 || publishedAt.getYear() > 9999) {
      throw new IllegalArgumentException("Invalid publication time");
    }
  }

  public SteamNewsItem(long appId, String sourceNewsId, String title, String content,
      String sourceUrl, LocalDateTime publishedAt) {
    this(appId, sourceNewsId, title, content, sourceUrl, publishedAt, List.of());
  }

  /** Length-prefix each UTF-8 field so delimiters inside content cannot cause collisions. */
  public String contentHash() {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (String field : new String[] {title, content, sourceUrl, publishedAt.toString()}) {
        byte[] bytes = field.replace("\r\n", "\n").replace('\r', '\n').getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  private static void requireText(String value, int limit, String field) {
    if (value == null || value.isBlank() || value.length() > limit) {
      throw new IllegalArgumentException("Invalid Steam " + field);
    }
  }
}
