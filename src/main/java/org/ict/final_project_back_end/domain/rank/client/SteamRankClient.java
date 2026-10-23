package org.ict.final_project_back_end.domain.rank.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ict.final_project_back_end.domain.rank.dto.SteamWeeklySnapshot;
import org.ict.final_project_back_end.domain.rank.dto.SteamWeeklySnapshot.Product;
import org.ict.final_project_back_end.domain.rank.exception.SteamRankFetchException;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** No API key, cookies, startup hook or scheduled invocation. */
@Component
public class SteamRankClient {
  private static final String ENDPOINT = "https://api.steampowered.com/IStoreTopSellersService/GetWeeklyTopSellers/v1/";
  private final HttpClient http;
  private final Clock clock;
  private final ObjectMapper json = new ObjectMapper();

  public SteamRankClient() {
    this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER).build(), Clock.systemUTC());
  }

  SteamRankClient(HttpClient http, Clock clock) {
    this.http = http;
    this.clock = clock;
  }

  /** null requests the latest published chart; explicit weeks must match exactly. */
  public SteamWeeklySnapshot fetchWeeklyRank(String countryCode, LocalDate weekStart) {
    String country = SteamWeeklySnapshot.normalizeCountry(countryCode);
    if (weekStart != null) validateWeek(weekStart);
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("country_code", country);
    input.put("context", Map.of("country_code", country, "language", "english"));
    input.put("data_request", Map.of("include_assets", true));
    input.put("page_start", 0);
    input.put("page_count", 10);
    if (weekStart != null) input.put("start_date", weekStart.atStartOfDay(ZoneOffset.UTC).toEpochSecond());
    try {
      URI uri = URI.create(ENDPOINT + "?input_json="
          + URLEncoder.encode(json.writeValueAsString(input), StandardCharsets.UTF_8));
      HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20))
          .header("Accept", "application/json").GET().build();
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() != 200) throw new SteamRankFetchException("Steam HTTP status: " + response.statusCode());
      if (!"1".equals(response.headers().firstValue("X-eresult").orElse(""))) {
        throw new SteamRankFetchException("Steam did not report a successful X-eresult");
      }
      String contentType = response.headers().firstValue("Content-Type").orElse("");
      if (!contentType.toLowerCase(Locale.ROOT).startsWith("application/json")
          || response.body() == null || response.body().length() > 1_000_000) {
        throw new SteamRankFetchException("Unexpected Steam response format or size");
      }
      return parse(country, weekStart, json.readTree(response.body()));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new SteamRankFetchException("Steam request interrupted", e);
    } catch (IOException e) {
      throw new SteamRankFetchException("Steam request or JSON decoding failed", e);
    } catch (IllegalArgumentException | ArithmeticException e) {
      throw new SteamRankFetchException("Invalid Steam weekly ranking response", e);
    }
  }

  private SteamWeeklySnapshot parse(String country, LocalDate requestedWeek, JsonNode root) {
    if (root == null) throw new IllegalArgumentException("Empty JSON");
    JsonNode response = root.path("response");
    long seconds = integer(response.path("start_date"));
    if (seconds <= 0 || seconds > 0xffffffffL || seconds % 86400 != 0) {
      throw new IllegalArgumentException("Invalid UTC week timestamp");
    }
    LocalDate week = Instant.ofEpochSecond(seconds).atOffset(ZoneOffset.UTC).toLocalDate();
    validateWeek(week);
    if (requestedWeek != null && !requestedWeek.equals(week)) throw new IllegalArgumentException("Wrong week returned");
    JsonNode ranks = response.path("ranks");
    if (!ranks.isArray() || ranks.size() != 10) throw new IllegalArgumentException("Incomplete TOP 10");
    var products = new ArrayList<Product>();
    for (JsonNode row : ranks) {
      int rank = Math.toIntExact(integer(row.path("rank")));
      long appId = integer(row.path("appid"));
      JsonNode item = row.path("item");
      if (!item.isObject() || !item.path("name").isTextual()) throw new IllegalArgumentException("Missing product name");
      // Existing tables support App IDs only. Do not silently discard packages/bundles.
      if ((item.has("item_type") && integer(item.get("item_type")) != 0)
          || (item.has("appid") && integer(item.get("appid")) != appId)
          || (item.has("id") && integer(item.get("id")) != appId)) {
        throw new IllegalArgumentException("Unsupported or mismatched product identity");
      }
      products.add(new Product(rank, appId, item.get("name").textValue(),
          optionalInt(row.get("last_week_rank")), optionalInt(row.get("consecutive_weeks")), image(item.path("assets")), firstTop100(row)));
    }
    products.sort(Comparator.comparingInt(Product::rank));
    return new SteamWeeklySnapshot(country, week, clock.instant(), products);
  }

  private void validateWeek(LocalDate week) {
    if (week.isBefore(LocalDate.of(1970, 1, 1)) || week.getDayOfWeek() != DayOfWeek.TUESDAY
        || week.plusDays(7).isAfter(LocalDate.now(clock.withZone(ZoneOffset.UTC)))) {
      throw new IllegalArgumentException("A completed Tuesday-based chart week is required");
    }
  }

  private static long integer(JsonNode node) {
    if (node == null || !node.isIntegralNumber() || !node.canConvertToLong()) {
      throw new IllegalArgumentException("Expected an integer");
    }
    return node.longValue();
  }

  private static Integer optionalInt(JsonNode node) {
    return node == null || node.isNull() ? null : Math.toIntExact(integer(node));
  }

  private static boolean firstTop100(JsonNode row) {
    // Steam protobuf bool defaults to false when omitted; reject malformed explicit values.
    JsonNode value = row.get("first_top100");
    if (value == null) return false;
    if (!value.isBoolean()) throw new IllegalArgumentException("Invalid first_top100 flag");
    return value.booleanValue();
  }

  private static String image(JsonNode assets) {
    String header = assets.path("header").asText("");
    String format = assets.path("asset_url_format").asText("");
    String candidate = header.startsWith("https://") ? header
        : format.contains("${FILENAME}") && !header.isBlank() ? format.replace("${FILENAME}", header) : "";
    try {
      // Steam's ConstructAssetURL prefixes relative asset templates with this CDN root.
      if (candidate.startsWith("steam/apps/")) {
        candidate = "https://shared.akamai.steamstatic.com/store_item_assets/" + candidate;
      }
      URI uri = URI.create(candidate);
      String host = uri.getHost();
      return "https".equals(uri.getScheme()) && uri.getUserInfo() == null && host != null
          && (host.endsWith(".steamstatic.com") || host.endsWith(".steampowered.com")) ? candidate : null;
    } catch (IllegalArgumentException e) {
      return null; // Keep the product and mark a metadata gap; never invent an image URL.
    }
  }
}
