package org.ict.final_project_back_end.domain.gamenews.client;

import org.ict.final_project_back_end.domain.gamenews.exception.SteamNewsException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ict.final_project_back_end.domain.gamenews.dto.SteamNewsItem;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@Component
public class SteamNewsClient {
  private static final String ENDPOINT = "https://api.steampowered.com/ISteamNews/GetNewsForApp/v2/";
  private final HttpClient http;
  private final ObjectMapper json = new ObjectMapper();

  public SteamNewsClient() {
    this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
        .followRedirects(HttpClient.Redirect.NEVER).build());
  }

  SteamNewsClient(HttpClient http) { this.http = http; }

  public List<SteamNewsItem> fetch(long appId, int count) {
    validateRequest(appId, count);
    URI uri = URI.create(ENDPOINT + "?appid=" + appId + "&count=" + count
        + "&maxlength=0&feeds=" + SteamNewsItem.OFFICIAL_FEED + "&format=json");
    HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8))
        .header("Accept", "application/json").GET().build();
    try {
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() != 200) throw new SteamNewsException("Steam HTTP " + response.statusCode());
      String contentType = response.headers().firstValue("Content-Type").orElse("");
      if (!contentType.toLowerCase(Locale.ROOT).startsWith("application/json")
          || response.body() == null || response.body().length() > 2_000_000) {
        throw new SteamNewsException("Unexpected Steam response format or size");
      }
      return parse(appId, count, json.readTree(response.body()));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new SteamNewsException("Steam request interrupted", e);
    } catch (IOException e) {
      throw new SteamNewsException("Steam request or JSON decoding failed", e);
    } catch (IllegalArgumentException | DateTimeException e) {
      throw new SteamNewsException("Invalid Steam news response", e);
    }
  }

  private List<SteamNewsItem> parse(long appId, int count, JsonNode root) {
    if (root == null) throw new IllegalArgumentException("Empty JSON");
    JsonNode data = root.path("appnews");
    if (integer(data.path("appid")) != appId) throw new IllegalArgumentException("Wrong app ID");
    JsonNode rows = data.path("newsitems");
    if (!rows.isArray() || rows.size() > count) throw new IllegalArgumentException("Invalid news list");
    Map<String, SteamNewsItem> items = new LinkedHashMap<>();
    for (JsonNode row : rows) {
      // Reject a mismatched game even when Steam includes it in an otherwise valid response.
      if (integer(row.path("appid")) != appId) throw new IllegalArgumentException("Wrong item app ID");
      String feed = text(row, "feedname");
      // is_external_url is also true for official Steam redirect URLs: never use it as the feed filter.
      if (!SteamNewsItem.OFFICIAL_FEED.equals(feed) || integer(row.path("feed_type")) != 1) continue;
      long seconds = integer(row.path("date"));
      if (seconds <= 0 || seconds > 0xffffffffL) throw new IllegalArgumentException("Invalid publication time");
      SteamNewsItem item = new SteamNewsItem(appId, text(row, "gid"), text(row, "title"),
          text(row, "contents"), text(row, "url"),
          LocalDateTime.ofInstant(Instant.ofEpochSecond(seconds), ZoneOffset.UTC), tags(row.path("tags")));
      SteamNewsItem previous = items.putIfAbsent(item.sourceNewsId(), item);
      if (previous != null && !previous.equals(item)) throw new IllegalArgumentException("Conflicting duplicate gid");
    }
    return List.copyOf(items.values());
  }

  private static List<String> tags(JsonNode values) {
    if (values.isMissingNode() || values.isNull()) return List.of();
    if (!values.isArray() || values.size() > 100) throw new IllegalArgumentException("Invalid tags");
    List<String> tags = new ArrayList<>();
    for (JsonNode value : values) {
      if (!value.isTextual() || value.textValue().length() > 200) throw new IllegalArgumentException("Invalid tag");
      tags.add(value.textValue());
    }
    return List.copyOf(tags);
  }

  public static void validateRequest(long appId, int count) {
    if (appId <= 0 || appId > 0xffffffffL) throw new IllegalArgumentException("Invalid Steam app ID");
    if (count < 1 || count > 100) throw new IllegalArgumentException("News count must be between 1 and 100");
  }

  private static long integer(JsonNode value) {
    if (!value.isIntegralNumber() || !value.canConvertToLong()) throw new IllegalArgumentException("Expected integer");
    return value.longValue();
  }

  private static String text(JsonNode row, String key) {
    JsonNode value = row.path(key);
    if (!value.isTextual()) throw new IllegalArgumentException("Missing text field: " + key);
    return value.textValue();
  }
}
