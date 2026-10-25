package org.ict.final_project_back_end.domain.aisearch.client;

import org.ict.final_project_back_end.domain.aisearch.exception.AiSearchException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

// 실제 검색 모델 없이 요청 본문 모양(snake_case)과 응답·오류 해석만 검증한다
class FastApiGameSearchModelClientTest {

  private static final String URL = "http://model/internal/v1/search";
  private final SearchKeywords keywords = new SearchKeywords(SearchKeywords.Intent.SEARCH, "",
      List.of("Horror"), List.of("Pixel Graphics"), "scary", "Phasmophobia");
  private MockRestServiceServer server;
  private FastApiGameSearchModelClient client;

  @BeforeEach
  void setup() {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://model");
    server = MockRestServiceServer.bindTo(builder).build();
    client = new FastApiGameSearchModelClient(builder.build());
  }

  @Test
  void sendsSnakeCaseBodyAndReadsItems() {
    server.expect(requestTo(URL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(jsonPath("$.tags[0]").value("Horror"))
        .andExpect(jsonPath("$.exclude_tags[0]").value("Pixel Graphics"))
        .andExpect(jsonPath("$.description").value("scary"))
        .andExpect(jsonPath("$.similar_to").value("Phasmophobia"))
        .andExpect(jsonPath("$.limit").value(5))
        .andRespond(withSuccess("""
            {"items":[{"app_id":739630,"score":4.4}],"ignored_tags":["Spooky"],"similar_to_app_id":739630}
            """, MediaType.APPLICATION_JSON));

    GameSearchModelClient.Response response = client.search(keywords, 5);

    assertEquals(739630L, response.items().getFirst().appId());
    assertEquals(List.of("Spooky"), response.ignoredTags());
    assertEquals(739630L, response.similarToAppId());
    server.verify();
  }

  @Test
  void noUsableKeywordsMeansNotUnderstood() {
    server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT));
    assertEquals("AI_SEARCH_NOT_UNDERSTOOD", assertThrows(AiSearchException.class, () -> client.search(keywords, 5)).getCode());
  }

  @Test
  void modelMissingOrDownMeansUnavailable() {
    server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
    assertEquals("AI_SEARCH_UNAVAILABLE", assertThrows(AiSearchException.class, () -> client.search(keywords, 5)).getCode());

    server.reset();
    server.expect(requestTo(URL)).andRespond(req -> {
      throw new ResourceAccessException("Connection refused");
    });
    assertEquals("AI_SEARCH_UNAVAILABLE", assertThrows(AiSearchException.class, () -> client.search(keywords, 5)).getCode());
  }
}
