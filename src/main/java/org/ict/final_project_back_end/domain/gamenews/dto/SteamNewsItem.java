package org.ict.final_project_back_end.domain.gamenews.dto;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Objects;

/** Validated official source; publishedAt is UTC. Keep HTML/BBCode as original text. */
public record SteamNewsItem(long appId, String sourceNewsId, String title, String content,
                            String sourceUrl, LocalDateTime publishedAt) {
  public static final String OFFICIAL_FEED = "steam_community_announcements";

  public SteamNewsItem {
    if (appId <= 0 || appId > 0xffffffffL) throw new IllegalArgumentException("Invalid Steam app ID");
    requireText(sourceNewsId, 64, "gid");
    if (!sourceNewsId.matches("[0-9]+")) throw new IllegalArgumentException("Invalid Steam gid");
    requireText(title, 1000, "title");
    Objects.requireNonNull(content, "content");
    requireText(sourceUrl, 2048, "url");
    URI uri = URI.create(sourceUrl);
    if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
        || uri.getHost() == null || uri.getUserInfo() != null) {
      throw new IllegalArgumentException("Invalid source URL");
    }
    Objects.requireNonNull(publishedAt, "publishedAt");
    if (publishedAt.getYear() < 1970 || publishedAt.getYear() > 9999) {
      throw new IllegalArgumentException("Invalid publication time");
    }
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
