package org.ict.final_project_back_end.domain.gamenews.client;

import org.ict.final_project_back_end.domain.gamenews.exception.SteamNewsException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.ict.final_project_back_end.domain.gamenews.dto.SteamNewsItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SteamNewsClientTest {
  private final ObjectMapper json = new ObjectMapper();
  private final HttpClient http = mock(HttpClient.class);
  @SuppressWarnings("unchecked") private final HttpResponse<String> response = mock(HttpResponse.class);
  private final SteamNewsClient client = new SteamNewsClient(http);

  @BeforeEach
  void setup() throws Exception {
    when(http.<String>send(any(), any())).thenReturn(response);
    when(response.statusCode()).thenReturn(200);
    when(response.headers()).thenReturn(HttpHeaders.of(Map.of("Content-Type", List.of("application/json; charset=utf-8")), (a,b) -> true));
    when(response.body()).thenReturn(fixture().toString());
  }

  @Test
  void requestsFullOfficialContentWithoutKeyAndPreservesLargeGidAndMarkup() throws Exception {
    var items = client.fetch(570, 5);
    assertEquals(1, items.size());
    assertEquals("18446744073709551615", items.getFirst().sourceNewsId());
    assertEquals("[b]패치[/b]\n전체 본문", items.getFirst().content());
    assertEquals(LocalDateTime.ofInstant(Instant.ofEpochSecond(1790000000), ZoneOffset.UTC), items.getFirst().publishedAt());
    var capture = ArgumentCaptor.forClass(HttpRequest.class);
    verify(http).send(capture.capture(), any());
    var request = capture.getValue();
    assertEquals("https://api.steampowered.com/ISteamNews/GetNewsForApp/v2/", request.uri().toString().split("\\?")[0]);
    assertEquals("appid=570&count=5&maxlength=0&feeds=steam_community_announcements&format=json", request.uri().getQuery());
    assertEquals(Duration.ofSeconds(8), request.timeout().orElseThrow());
  }

  @Test
  void excludesThirdPartyFeedsAndDeduplicatesIdenticalItems() {
    var body = fixture();
    var rows = body.withObject("appnews").withArray("newsitems");
    rows.add(rows.get(0).deepCopy());
    rows.add(((ObjectNode) rows.get(0).deepCopy()).put("feedname", "pcgamer").put("gid", "123"));
    when(response.body()).thenReturn(body.toString());
    assertEquals(1, client.fetch(570, 5).size());
    rows.removeAll();
    when(response.body()).thenReturn(body.toString());
    assertTrue(client.fetch(570, 5).isEmpty());
  }

  @Test
  void rejectsWrongGameMissingBodyInvalidFieldsAndConflictingDuplicates() {
    List<Consumer<ObjectNode>> mutations = List.of(
        b -> b.withObject("appnews").put("appid", 730),
        b -> row(b).put("appid", 730),
        b -> row(b).remove("contents"),
        b -> row(b).put("gid", 123),
        b -> row(b).put("date", 1790000000000L),
        b -> row(b).put("url", "javascript:alert(1)"),
        b -> row(b).put("title", "a".repeat(1001)),
        b -> b.withObject("appnews").withArray("newsitems").add(row(b).deepCopy().put("contents", "conflict")));
    for (var mutation : mutations) {
      var body = fixture(); mutation.accept(body);
      when(response.body()).thenReturn(body.toString());
      assertThrows(SteamNewsException.class, () -> client.fetch(570, 5));
    }
  }

  @Test
  void rejectsHttpErrorsMalformedAndMissingResponses() {
    for (int status : new int[] {302, 403, 429, 500}) {
      when(response.statusCode()).thenReturn(status);
      assertThrows(SteamNewsException.class, () -> client.fetch(570, 5));
    }
    when(response.statusCode()).thenReturn(200);
    for (String body : List.of("null", "{}", "not json", "{\"appnews\":{\"appid\":570}}")) {
      when(response.body()).thenReturn(body);
      assertThrows(SteamNewsException.class, () -> client.fetch(570, 5));
    }
    when(response.body()).thenReturn(fixture().toString());
    when(response.headers()).thenReturn(HttpHeaders.of(Map.of("Content-Type", List.of("text/html")), (a,b) -> true));
    assertThrows(SteamNewsException.class, () -> client.fetch(570, 5));
  }

  @Test
  void validatesArgumentsBeforeSendingAndHandlesTransportFailure() throws Exception {
    assertThrows(IllegalArgumentException.class, () -> client.fetch(0, 5));
    assertThrows(IllegalArgumentException.class, () -> client.fetch(570, 101));
    verifyNoInteractions(http);
    when(http.<String>send(any(), any())).thenThrow(new IOException("connection failed"));
    assertThrows(SteamNewsException.class, () -> client.fetch(570, 5));
  }

  @Test
  void preservesThreadInterruption() throws Exception {
    when(http.<String>send(any(), any())).thenThrow(new InterruptedException());
    try {
      assertThrows(SteamNewsException.class, () -> client.fetch(570, 5));
      assertTrue(Thread.currentThread().isInterrupted());
    } finally { Thread.interrupted(); }
  }

  @Test
  void hashDetectsEverySourceFieldButNormalizesLineEndings() {
    var time = LocalDateTime.of(2026, 10, 24, 0, 0);
    var original = new SteamNewsItem(570, "1", "Title", "Line1\r\nLine2", "https://steamcommunity.com/a", time);
    assertEquals(64, original.contentHash().length());
    assertEquals(original.contentHash(), new SteamNewsItem(570, "1", "Title", "Line1\nLine2", "https://steamcommunity.com/a", time).contentHash());
    for (var changed : List.of(
        new SteamNewsItem(570, "1", "New title", original.content(), original.sourceUrl(), time),
        new SteamNewsItem(570, "1", original.title(), "Edited body", original.sourceUrl(), time),
        new SteamNewsItem(570, "1", original.title(), original.content(), "https://steamcommunity.com/b", time),
        new SteamNewsItem(570, "1", original.title(), original.content(), original.sourceUrl(), time.plusSeconds(1)))) {
      assertNotEquals(original.contentHash(), changed.contentHash());
    }
  }

  private ObjectNode fixture() {
    var root = json.createObjectNode();
    root.putObject("appnews").put("appid", 570).putArray("newsitems").addObject()
        .put("appid", 570).put("gid", "18446744073709551615").put("feedname", SteamNewsItem.OFFICIAL_FEED).put("feed_type", 1)
        .put("title", "패치 노트").put("contents", "[b]패치[/b]\n전체 본문")
        .put("url", "https://steamcommunity.com/games/dota2/announcements/detail/123")
        .put("date", 1790000000);
    return root;
  }

  @Test void officialRedirectIsNotAThirdPartyArticleAndTagsSurvive() {
    var body = fixture();
    row(body).put("is_external_url", true)
        .put("url", "http://steamstore-a.akamaihd.net/news/externalpost/steam_community_announcements/123")
        .putArray("tags").add("patchnotes");
    when(response.body()).thenReturn(body.toString());
    var item = client.fetch(570, 5).getFirst();
    assertEquals(List.of("patchnotes"), item.tags());
    assertTrue(item.sourceUrl().startsWith("https://"));
    row(body).put("feed_type", 0);
    when(response.body()).thenReturn(body.toString());
    assertTrue(client.fetch(570, 5).isEmpty());
  }

  @Test void excludesExternalMediaEvenWhenUrlIsSteamAndRejectsUnsafeOfficialLinks() {
    var body = fixture();
    row(body).put("feedname", "pcgamer");
    when(response.body()).thenReturn(body.toString());
    assertTrue(client.fetch(570, 5).isEmpty());
    for (String url : List.of("https://pcgamer.com/story", "https://steamcommunity.com.evil.test/a",
        "https://user@steamcommunity.com/a", "https://steamcommunity.com:444/a")) {
      body = fixture(); row(body).put("url", url);
      when(response.body()).thenReturn(body.toString());
      assertThrows(SteamNewsException.class, () -> client.fetch(570, 5));
    }
  }

  private ObjectNode row(ObjectNode root) { return (ObjectNode) root.path("appnews").path("newsitems").get(0); }
}

