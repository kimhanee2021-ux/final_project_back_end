package org.ict.final_project_back_end.domain.search.service;

import org.ict.final_project_back_end.domain.game.entity.Game;
import org.ict.final_project_back_end.domain.game.repository.GameRepository;
import org.ict.final_project_back_end.domain.search.client.KeywordExtractionException;
import org.ict.final_project_back_end.domain.search.client.KeywordExtractor;
import org.ict.final_project_back_end.domain.search.client.SearchKeywords;
import org.ict.final_project_back_end.domain.search.client.SearchModelClient;
import org.ict.final_project_back_end.domain.search.client.dto.SearchModelRequest;
import org.ict.final_project_back_end.domain.search.client.dto.SearchModelResult;
import org.ict.final_project_back_end.domain.search.dto.NaturalSearchResponse;
import org.ict.final_project_back_end.domain.search.exception.SearchException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// AI와 모델 서버, DB 없이 검색 흐름(분석 / 대체 경로 / 결과 조합)만 검증한다
class SearchServiceImplTest {

  private static final long USER_ID = 5L;

  private final FakeExtractor extractor = new FakeExtractor();
  private final FakeModelClient model = new FakeModelClient();
  private final GameRepository gameRepository = mock(GameRepository.class);

  private SearchServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new SearchServiceImpl(extractor, model, gameRepository, new SearchRateLimit());
    extractor.keywords = new SearchKeywords(List.of("Farming Sim"), List.of("Horror"), "a cozy farming game", "Stardew Valley");
    model.result = new SearchModelResult(List.of(new SearchModelResult.Item(20L, 3.2), new SearchModelResult.Item(10L, 1.5)),
        List.of("Super Hard"), 413150L);
    when(gameRepository.findAllById(anyList())).thenReturn(List.of(game(10), game(20)));
  }

  @Test
  void sendsTheKeywordsAndLimitToTheModelServer() {
    service.search(USER_ID, "  스타듀밸리 같은 농사 게임  ", 7);

    SearchModelRequest request = model.requests.get(0);
    assertEquals(List.of("Farming Sim"), request.tags());
    assertEquals(List.of("Horror"), request.excludeTags());
    assertEquals("a cozy farming game", request.description());
    assertEquals("Stardew Valley", request.similarTo());
    assertEquals(7, request.limit());
    assertEquals("스타듀밸리 같은 농사 게임", extractor.queries.get(0)); // 앞뒤 공백은 뗀다
  }

  @Test
  void keepsTheModelRankAndAttachesGameInfoFromTheCatalog() {
    NaturalSearchResponse response = service.search(USER_ID, "농사 게임", 10);

    assertEquals(List.of(20L, 10L), response.results().stream().map(NaturalSearchResponse.Item::appId).toList());
    assertEquals(List.of(1, 2), response.results().stream().map(NaturalSearchResponse.Item::rank).toList());
    assertEquals("Game 20", response.results().get(0).game().name());
    assertEquals(3.2, response.results().get(0).score());
  }

  @Test
  void gamesMissingFromTheCatalogComeBackWithNullGame() {
    when(gameRepository.findAllById(anyList())).thenReturn(List.of(game(20)));

    NaturalSearchResponse response = service.search(USER_ID, "농사 게임", 10);

    assertEquals(10L, response.results().get(1).appId());
    assertNull(response.results().get(1).game());
  }

  @Test
  void tellsTheClientHowTheQueryWasUnderstood() {
    NaturalSearchResponse response = service.search(USER_ID, "농사 게임", 10);

    assertTrue(response.analysis().analyzed());
    assertEquals(List.of("Farming Sim"), response.analysis().tags());
    assertEquals(List.of("Horror"), response.analysis().excludeTags());
    assertEquals(List.of("Super Hard"), response.analysis().ignoredTags());
    assertEquals("농사 게임", response.query());
  }

  @Test
  void whenTheAiFailsTheQueryItselfIsSearchedAndMarkedAsNotAnalyzed() {
    extractor.failure = new KeywordExtractionException("혼잡");

    NaturalSearchResponse response = service.search(USER_ID, "잔잔한 농사 게임", 10);

    assertEquals("잔잔한 농사 게임", model.requests.get(0).description());
    assertEquals(List.of(), model.requests.get(0).tags());
    assertFalse(response.analysis().analyzed());
    assertEquals(2, response.results().size());
  }

  @Test
  void whenTheAiOnlyExcludesThingsTheQueryBecomesTheDescription() {
    extractor.keywords = new SearchKeywords(List.of(), List.of("Horror"), "", "");

    service.search(USER_ID, "공포 빼고 아무거나", 10);

    SearchModelRequest request = model.requests.get(0);
    assertEquals("공포 빼고 아무거나", request.description());
    assertEquals(List.of("Horror"), request.excludeTags());
  }

  @Test
  void aModelServerFailureIsNotHiddenByTheFallback() {
    model.failure = SearchException.serverError();

    assertThrows(SearchException.class, () -> service.search(USER_ID, "농사 게임", 10));
  }

  @Test
  void emptyResultsAreAnEmptyList() {
    model.result = new SearchModelResult(List.of(), null, null);
    when(gameRepository.findAllById(anyList())).thenReturn(List.of());

    NaturalSearchResponse response = service.search(USER_ID, "농사 게임", 10);

    assertEquals(List.of(), response.results());
    assertEquals(List.of(), response.analysis().ignoredTags());
  }

  @Test
  void stopsBeforeCallingTheAiWhenTheRateLimitIsExceeded() {
    for (int i = 0; i < SearchRateLimit.MAX_PER_MINUTE; i++) {
      service.search(USER_ID, "농사 게임", 10);
    }
    int callsBefore = extractor.queries.size();

    assertThrows(SearchException.class, () -> service.search(USER_ID, "농사 게임", 10));
    assertEquals(callsBefore, extractor.queries.size());
  }

  private static Game game(long appId) {
    return Game.builder().appId(appId).name("Game " + appId).genre("Indie").build();
  }

  private static class FakeExtractor implements KeywordExtractor {
    SearchKeywords keywords;
    KeywordExtractionException failure;
    final List<String> queries = new ArrayList<>();

    @Override
    public SearchKeywords extract(String query) {
      queries.add(query);
      if (failure != null) {
        throw failure;
      }
      return keywords;
    }
  }

  private static class FakeModelClient implements SearchModelClient {
    SearchModelResult result;
    SearchException failure;
    final List<SearchModelRequest> requests = new ArrayList<>();

    @Override
    public SearchModelResult search(SearchModelRequest request) {
      requests.add(request);
      if (failure != null) {
        throw failure;
      }
      return result;
    }
  }
}
