package org.ict.final_project_back_end.domain.rank.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.ict.final_project_back_end.domain.rank.exception.SteamRankFetchException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.net.URLDecoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SteamRankClientTest {
  private static final LocalDate WEEK = LocalDate.of(2026, 10, 13);
  private final ObjectMapper json = new ObjectMapper();
  private final HttpClient http = mock(HttpClient.class);
  @SuppressWarnings("unchecked")
  private final HttpResponse<String> response = mock(HttpResponse.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-10-21T05:00:00Z"), ZoneOffset.UTC);
  private final SteamRankClient client = new SteamRankClient(http, clock);

  @BeforeEach
  void setup() throws Exception {
    when(http.<String>send(any(), any())).thenReturn(response);
    reply(200, "1", "application/json; charset=UTF-8", fixture().toString());
  }

  @Test
  void buildsKeylessCountryContextAndUnixSecondsAndParsesTopTen() throws Exception {
    for (String country : List.of("KR", "US")) {
      var result = client.fetchWeeklyRank(country.toLowerCase(Locale.ROOT), WEEK);
      assertEquals(country, result.country());
      assertEquals(WEEK, result.week());
      assertEquals(clock.instant(), result.fetchedAt());
      assertEquals("VERIFIED", result.status());
      assertEquals(10, result.products().size());
      assertEquals(1010L, result.products().get(9).appId());
      assertEquals("https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/1010/header.jpg", result.products().get(9).imageUrl());
      assertEquals("UP", result.products().get(9).changeKind());
    }
    var capture = ArgumentCaptor.forClass(HttpRequest.class);
    verify(http, times(2)).send(capture.capture(), any());
    for (int i = 0; i < 2; i++) {
      var request = capture.getAllValues().get(i);
      assertEquals("GET", request.method());
      assertEquals("api.steampowered.com", request.uri().getHost());
      assertEquals("/IStoreTopSellersService/GetWeeklyTopSellers/v1/", request.uri().getPath());
      assertEquals(Duration.ofSeconds(20), request.timeout().orElseThrow());
      assertTrue(request.headers().firstValue("Authorization").isEmpty());
      assertTrue(request.headers().firstValue("Cookie").isEmpty());
      String query = request.uri().getRawQuery();
      assertTrue(query.startsWith("input_json="));
      assertFalse(query.contains("&"));
      var input = json.readTree(URLDecoder.decode(query.substring(11), StandardCharsets.UTF_8));
      assertEquals(List.of("KR", "US").get(i), input.path("country_code").asText());
      assertEquals(input.path("country_code"), input.path("context").path("country_code"));
      assertEquals(10, input.path("page_count").asInt());
      assertEquals(0, input.path("page_start").asInt());
      assertEquals(1791849600L, input.path("start_date").asLong());
      assertTrue(input.path("data_request").path("include_assets").asBoolean());
      assertFalse(input.has("key"));
    }
  }

  @Test
  void latestOmitsDateAndHistoricalWeekIsExact() throws Exception {
    assertEquals(WEEK, client.fetchWeeklyRank("KR", null).week());
    var capture = ArgumentCaptor.forClass(HttpRequest.class);
    verify(http).send(capture.capture(), any());
    var input = json.readTree(URLDecoder.decode(capture.getValue().uri().getRawQuery().substring(11), StandardCharsets.UTF_8));
    assertFalse(input.has("start_date"));
    var past = fixture();
    ((ObjectNode)past.get("response")).put("start_date", 1791244800L);
    when(response.body()).thenReturn(past.toString());
    assertEquals(WEEK.minusDays(7), client.fetchWeeklyRank("US", WEEK.minusDays(7)).week());
    assertThrows(SteamRankFetchException.class, () -> client.fetchWeeklyRank("US", WEEK));
  }

  @Test
  void rejectsBadStatusResultFormatAndJson() {
    for (int status : new int[] {302, 403, 429, 500}) {
      reply(status, "1", "application/json", fixture().toString());
      assertThrows(SteamRankFetchException.class, () -> client.fetchWeeklyRank("KR", WEEK));
    }
    for (String result : new String[] {"8", "", "15"}) {
      reply(200, result, "application/json", fixture().toString());
      assertThrows(SteamRankFetchException.class, () -> client.fetchWeeklyRank("KR", WEEK));
    }
    reply(200, "1", "text/html", "<html>error</html>");
    assertThrows(SteamRankFetchException.class, () -> client.fetchWeeklyRank("KR", WEEK));
    for (String body : List.of("not json", "null", "{}", "{\"response\":{}}")) {
      reply(200, "1", "application/json", body);
      assertThrows(SteamRankFetchException.class, () -> client.fetchWeeklyRank("KR", WEEK));
    }
  }

  @Test
  void rejectsInvalidTimestampCountRankAndIdentityWithoutDroppingItems() {
    List<java.util.function.Consumer<ObjectNode>> changes = List.of(
        body -> ((ObjectNode)body.get("response")).put("start_date", "2026-10-13"),
        body -> ((ObjectNode)body.get("response")).put("start_date", 1791849600000L),
        body -> ((ObjectNode)body.get("response")).put("start_date", 4294512000L),
        body -> ((ObjectNode)body.get("response")).put("start_date", 1791936000L),
        body -> ranks(body).remove(9),
        body -> ranks(body).add(ranks(body).get(0).deepCopy()),
        body -> row(body, 9).put("rank", 1),
        body -> row(body, 9).put("rank", 11),
        body -> row(body, 9).put("rank", 10.5),
        body -> row(body, 9).put("appid", 1001),
        body -> row(body, 9).remove("appid"),
        body -> row(body, 9).put("appid", 0),
        body -> ((ObjectNode)row(body, 9).get("item")).put("item_type", 1),
        body -> ((ObjectNode)row(body, 9).get("item")).put("appid", 999),
        body -> ((ObjectNode)row(body, 9).get("item")).put("name", " "),
        body -> row(body, 9).put("last_week_rank", -1),
        body -> row(body, 9).put("consecutive_weeks", 999999999999L));
    for (var change : changes) {
      var body = fixture();change.accept(body);when(response.body()).thenReturn(body.toString());
      assertThrows(SteamRankFetchException.class, () -> client.fetchWeeklyRank("KR", WEEK));
    }
  }

  @Test
  void preservesMissingMetadataAndUsesProtobufDefaultForReturning() {
    var body = fixture();
    ((ObjectNode)row(body, 0).get("item")).remove("assets");
    row(body, 0).remove("last_week_rank");row(body, 0).remove("consecutive_weeks");
    row(body, 1).put("last_week_rank", 0);
    row(body, 2).put("last_week_rank", 187);
    row(body, 3).put("last_week_rank", 1);
    row(body, 4).put("last_week_rank", 5);
    when(response.body()).thenReturn(body.toString());
    var result = client.fetchWeeklyRank("KR", WEEK);
    assertEquals(10, result.products().size());
    assertEquals("VERIFIED_GAPS", result.status());
    assertNull(result.products().get(0).imageUrl());
    assertNull(result.products().get(0).previousRank());
    assertEquals(List.of("RETURNING", "RETURNING", "RETURNING", "DOWN", "SAME"),
        result.products().subList(0, 5).stream().map(p -> p.changeKind()).toList());
  }

  @Test
  void rejectsInvalidInputBeforeHttp() {
    assertThrows(IllegalArgumentException.class, () -> client.fetchWeeklyRank("JP", WEEK));
    assertThrows(IllegalArgumentException.class, () -> client.fetchWeeklyRank("KR", WEEK.plusDays(1)));
    assertThrows(IllegalArgumentException.class, () -> client.fetchWeeklyRank("KR", WEEK.plusDays(7)));
    verifyNoInteractions(http);
  }

  @Test
  void propagatesTimeoutAndRestoresInterrupt() throws Exception {
    when(http.<String>send(any(), any())).thenThrow(new HttpTimeoutException("test timeout"));
    assertThrows(SteamRankFetchException.class, () -> client.fetchWeeklyRank("KR", WEEK));
    doThrow(new InterruptedException("test interrupt")).when(http).send(any(), any());
    try {
      assertThrows(SteamRankFetchException.class, () -> client.fetchWeeklyRank("KR", WEEK));
      assertTrue(Thread.currentThread().isInterrupted());
    } finally { Thread.interrupted(); }
  }

  @Test
  void resolvesActualRelativeAssetsAndMatchesOfficialNewReturningRules() {
    var body = fixture();
    var assets = (ObjectNode)row(body, 0).path("item").path("assets");
    assets.put("asset_url_format", "steam/apps/1001/${FILENAME}?t=1786493086");
    assets.put("header", "hashed/header.jpg");
    row(body, 0).remove("last_week_rank");
    row(body, 0).put("first_top100", true);
    row(body, 1).put("last_week_rank", 154);
    row(body, 2).put("last_week_rank", 0).put("first_top100", false);
    row(body, 3).put("last_week_rank", 100).put("first_top100", true);
    when(response.body()).thenReturn(body.toString());
    var result = client.fetchWeeklyRank("US", WEEK);
    assertEquals("https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/1001/hashed/header.jpg?t=1786493086", result.products().getFirst().imageUrl());
    assertEquals(List.of("NEW", "RETURNING", "RETURNING", "UP"), result.products().subList(0,4).stream().map(p -> p.changeKind()).toList());
    assertEquals("VERIFIED", result.status());
  }

  @Test
  void rejectsMalformedFirstEntryFlags() {
    for (var value : List.of(json.nullNode(), json.getNodeFactory().textNode("true"), json.getNodeFactory().numberNode(1))) {
      var body = fixture();row(body, 0).set("first_top100", value);
      when(response.body()).thenReturn(body.toString());
      assertThrows(SteamRankFetchException.class, () -> client.fetchWeeklyRank("US", WEEK));
    }
  }

  @Test
  void invalidImageLocationsRemainGaps() {
    for (String format : List.of("//evil.example/${FILENAME}", "https://steamstatic.com.evil.example/${FILENAME}", "http://shared.akamai.steamstatic.com/${FILENAME}", "https://user@shared.akamai.steamstatic.com/${FILENAME}", "", "https://[bad/${FILENAME}")) {
      var body = fixture();
      ((ObjectNode)row(body, 0).path("item").path("assets")).put("asset_url_format", format);
      when(response.body()).thenReturn(body.toString());
      var result = client.fetchWeeklyRank("KR", WEEK);
      assertNull(result.products().getFirst().imageUrl());
      assertEquals("VERIFIED_GAPS", result.status());
      assertEquals(10, result.products().size());
    }
  }

  private ObjectNode fixture() {
    var body = json.createObjectNode();var response = body.putObject("response");
    response.put("start_date", 1791849600L);response.put("next_page_start", 10);
    var ranks = response.putArray("ranks");
    for (int i = 1; i <= 10; i++) {
      var row = ranks.addObject();row.put("rank", i);row.put("appid", 1000 + i);
      row.put("last_week_rank", i + 1);row.put("consecutive_weeks", 2);
      var item = row.putObject("item");item.put("item_type", 0);item.put("appid", 1000 + i);item.put("name", "Product " + i);
      var assets = item.putObject("assets");assets.put("header", "header.jpg");
      assets.put("asset_url_format", "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/" + (1000+i) + "/${FILENAME}");
    }
    return body;
  }
  private ArrayNode ranks(ObjectNode body) { return (ArrayNode)body.path("response").path("ranks"); }
  private ObjectNode row(ObjectNode body, int index) { return (ObjectNode)ranks(body).get(index); }
  private void reply(int status, String result, String contentType, String body) {
    when(response.statusCode()).thenReturn(status);
    when(response.headers()).thenReturn(HttpHeaders.of(Map.of("X-eresult", List.of(result), "Content-Type", List.of(contentType)), (a,b) -> true));
    when(response.body()).thenReturn(body);
  }
}
