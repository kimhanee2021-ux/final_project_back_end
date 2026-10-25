package org.ict.final_project_back_end.domain.aisearch.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.ict.final_project_back_end.domain.aisearch.exception.AiSearchException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

// 실제 OpenAI 없이 요청 모양과 Responses API 봉투 해석만 검증한다
class SearchKeywordClientTest {

  private static String envelope(String answer) {
    try {
      return """
          {"status":"completed","output":[{"type":"message","role":"assistant","status":"completed",
            "content":[{"type":"output_text","text":%s}]}]}
          """.formatted(new ObjectMapper().writeValueAsString(answer));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private record Setup(MockRestServiceServer server, SearchKeywordClient client) {
  }

  private Setup setup(String key) {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://openai");
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    return new Setup(server, new SearchKeywordClient(key, "gpt-5.4-mini", builder));
  }

  @Test
  void sendsStrictSchemaAndReadsKeywords() {
    Setup s = setup("key");
    s.server().expect(requestTo("http://openai/v1/responses"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer key"))
        .andExpect(jsonPath("$.model").value("gpt-5.4-mini"))
        .andExpect(jsonPath("$.text.format.strict").value(true))
        .andExpect(jsonPath("$.text.format.schema.properties.intent.enum[2]").value("LATEST_NEWS"))
        .andExpect(jsonPath("$.input").value("{\"request\":\"친구랑 할 무서운 게임\"}"))
        .andRespond(withSuccess(envelope("""
            {"intent":"SEARCH","gameTitle":"","tags":["Horror","Co-op","Horror"," "],"excludeTags":[],
             "description":"A scary co-op game.","similarTo":""}
            """), MediaType.APPLICATION_JSON));

    SearchKeywords keywords = s.client().extract("친구랑 할 무서운 게임");

    assertEquals(SearchKeywords.Intent.SEARCH, keywords.intent());
    assertEquals(List.of("Horror", "Co-op"), keywords.tags());
    assertEquals("A scary co-op game.", keywords.description());
    assertFalse(keywords.hasNoKeywords());
    s.server().verify();
  }

  @Test
  void readsNamedGameAndDropsKeywordsForBriefingControls() throws Exception {
    Setup s = setup("key");
    SearchKeywords news = s.client().parse(envelope("""
        {"intent":"LATEST_NEWS","gameTitle":"PUBG: BATTLEGROUNDS","tags":[],"excludeTags":[],"description":"","similarTo":"PUBG: BATTLEGROUNDS"}
        """));
    assertEquals(SearchKeywords.Intent.LATEST_NEWS, news.intent());
    assertEquals("PUBG: BATTLEGROUNDS", news.gameTitle());

    // 조작 명령에 모델이 게임·키워드를 잘못 채워도 쓰지 않는다
    SearchKeywords pause = s.client().parse(envelope("""
        {"intent":"PAUSE","gameTitle":"Dota 2","tags":["MOBA"],"excludeTags":[],"description":"x","similarTo":"y"}
        """));
    assertTrue(pause.intent().isControl());
    assertEquals("", pause.gameTitle());
    assertTrue(pause.hasNoKeywords());
  }

  @Test
  void capsTagCountAndDropsOverlongText() throws Exception {
    Setup s = setup("key");
    SearchKeywords keywords = s.client().parse(envelope("""
        {"intent":"SEARCH","gameTitle":"","tags":["a","b","c","d","e","f","g","h"],"excludeTags":[],"description":"%s","similarTo":"Elden Ring"}
        """.formatted("x".repeat(300))));
    assertEquals(SearchKeywordClient.MAX_TAGS, keywords.tags().size());
    assertEquals("", keywords.description());
    assertEquals("Elden Ring", keywords.similarTo());
  }

  @Test
  void unknownIntentIsUnavailable() {
    Setup s = setup("key");
    AiSearchException e = assertThrows(AiSearchException.class, () -> s.client().parse(envelope("""
        {"intent":"DANCE","gameTitle":"","tags":[],"excludeTags":[],"description":"","similarTo":""}
        """)));
    assertEquals("AI_SEARCH_UNAVAILABLE", e.getCode());
  }

  @Test
  void missingKeyAndRejectedKeyMeanNotConfigured() {
    assertEquals("AI_SEARCH_NOT_CONFIGURED",
        assertThrows(AiSearchException.class, () -> setup("").client().extract("공포 게임")).getCode());

    Setup s = setup("bad");
    s.server().expect(requestTo("http://openai/v1/responses")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
    assertEquals("AI_SEARCH_NOT_CONFIGURED", assertThrows(AiSearchException.class, () -> s.client().extract("공포 게임")).getCode());
  }

  @Test
  void rateLimitAndBrokenEnvelope() {
    Setup s = setup("key");
    s.server().expect(requestTo("http://openai/v1/responses")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
    assertEquals("AI_SEARCH_LIMITED", assertThrows(AiSearchException.class, () -> s.client().extract("공포 게임")).getCode());

    s.server().reset();
    s.server().expect(requestTo("http://openai/v1/responses"))
        .andRespond(withSuccess("{\"status\":\"incomplete\",\"output\":[]}", MediaType.APPLICATION_JSON));
    assertEquals("AI_SEARCH_UNAVAILABLE", assertThrows(AiSearchException.class, () -> s.client().extract("공포 게임")).getCode());
  }
}
