package org.ict.final_project_back_end.domain.aisearch.service;

import org.ict.final_project_back_end.domain.aisearch.client.GameSearchModelClient;
import org.ict.final_project_back_end.domain.aisearch.client.SearchKeywordClient;
import org.ict.final_project_back_end.domain.aisearch.client.SearchKeywords;
import org.ict.final_project_back_end.domain.aisearch.client.SearchKeywords.Intent;
import org.ict.final_project_back_end.domain.aisearch.dto.AiGameSearchResponse;
import org.ict.final_project_back_end.domain.aisearch.exception.AiSearchException;
import org.ict.final_project_back_end.domain.game.entity.Game;
import org.ict.final_project_back_end.domain.game.repository.GameRepository;
import org.ict.final_project_back_end.global.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiGameSearchServiceImplTest {

  private final SearchKeywordClient keywordClient = mock(SearchKeywordClient.class);
  private final GameSearchModelClient model = mock(GameSearchModelClient.class);
  private final GameRepository games = mock(GameRepository.class);
  private final AiGameSearchServiceImpl service = new AiGameSearchServiceImpl(keywordClient, model, games);

  private static SearchKeywords keywords(Intent intent, String title, List<String> tags, String similarTo) {
    return new SearchKeywords(intent, title, tags, List.of(), "", similarTo);
  }

  @Test
  void extractsKeywordsSearchesAndAttachesGameInfo() {
    SearchKeywords keywords = new SearchKeywords(Intent.SEARCH, "", List.of("Horror", "Co-op"), List.of(), "A scary co-op game.", "Phasmophobia");
    when(keywordClient.extract("친구랑 할 무서운 게임")).thenReturn(keywords);
    when(model.search(eq(keywords), anyInt())).thenReturn(new GameSearchModelClient.Response(
        List.of(new GameSearchModelClient.Item(1274570, 4.1), new GameSearchModelClient.Item(999, 3.0)),
        List.of("Spooky"), 739630L));
    when(games.findAllById(anyList())).thenReturn(List.of(
        Game.builder().appId(1274570L).name("DEVOUR").genre("Action,Indie").tags("Horror,Co-op").thumbnailUrl("http://img").build(),
        Game.builder().appId(739630L).name("Phasmophobia").build()));

    AiGameSearchResponse response = service.search("  친구랑 할 무서운 게임 ", 5);

    assertEquals("친구랑 할 무서운 게임", response.query());
    assertEquals("SEARCH", response.intent());
    assertNull(response.matchedGame());
    assertEquals(List.of("Horror", "Co-op"), response.keywords().tags());
    assertEquals(List.of("Spooky"), response.ignoredTags());
    assertEquals("Phasmophobia", response.similarTo().name());
    assertEquals(1, response.items().getFirst().rank());
    assertEquals("DEVOUR", response.items().getFirst().game().name());
    assertNull(response.items().get(1).game());
    verify(games, never()).findNameCandidates(anyString());
  }

  @Test
  void namedGameIsShownFirstAndLeftOutOfSimilarGames() {
    Game pubg = Game.builder().appId(578080L).name("PUBG: BATTLEGROUNDS").build();
    when(keywordClient.extract("배그 최신 소식 들려줘"))
        .thenReturn(keywords(Intent.LATEST_NEWS, "PUBG: BATTLEGROUNDS", List.of(), ""));
    // 후보는 제목 앞부분("pubg")으로 찾고, 그중 이름이 정확히 같은 게임을 고른다
    when(games.findNameCandidates("pubg%")).thenReturn(List.of(
        Game.builder().appId(1L).name("PUBG: Experimental Server").build(), pubg));
    when(model.search(any(), anyInt())).thenReturn(new GameSearchModelClient.Response(
        List.of(new GameSearchModelClient.Item(578080, 9.0), new GameSearchModelClient.Item(1172470, 5.0)), List.of(), 578080L));
    when(games.findAllById(anyList())).thenReturn(List.of(pubg, Game.builder().appId(1172470L).name("Apex Legends").build()));

    AiGameSearchResponse response = service.search("배그 최신 소식 들려줘", 5);

    assertEquals("LATEST_NEWS", response.intent());
    assertEquals(578080L, response.matchedGame().appId());
    // 비슷한 게임 검색은 말한 게임을 기준(similarTo)으로 하고, 말한 게임 자체는 목록에서 뺀다
    ArgumentCaptor<SearchKeywords> sent = ArgumentCaptor.forClass(SearchKeywords.class);
    verify(model).search(sent.capture(), anyInt());
    assertEquals("PUBG: BATTLEGROUNDS", sent.getValue().similarTo());
    assertEquals(List.of(1172470L), response.items().stream().map(AiGameSearchResponse.Item::appId).toList());
    assertEquals(1, response.items().getFirst().rank());
  }

  @Test
  void namedGameFallsBackToPrefixAndSurvivesUnusableKeywords() {
    Game gtfo = Game.builder().appId(493520L).name("GTFO").build();
    when(keywordClient.extract("GTF")).thenReturn(keywords(Intent.FIND_GAME, "GTF", List.of(), ""));
    when(games.findNameCandidates("gtf%")).thenReturn(List.of(gtfo));
    when(model.search(any(), anyInt())).thenThrow(AiSearchException.notUnderstood());

    AiGameSearchResponse response = service.search("GTF", 5);

    assertEquals(493520L, response.matchedGame().appId());
    assertTrue(response.items().isEmpty());
  }

  @Test
  void briefingControlsSkipSearch() {
    when(keywordClient.extract("멈춰")).thenReturn(keywords(Intent.PAUSE, "", List.of(), ""));
    AiGameSearchResponse response = service.search("멈춰", 5);
    assertEquals("PAUSE", response.intent());
    assertTrue(response.items().isEmpty());
    verifyNoInteractions(model, games);
  }

  @Test
  void nothingUsableNeverCallsModel() {
    when(keywordClient.extract("오늘 날씨 어때")).thenReturn(keywords(Intent.SEARCH, "", List.of(), ""));
    AiSearchException e = assertThrows(AiSearchException.class, () -> service.search("오늘 날씨 어때", 5));
    assertEquals("AI_SEARCH_NOT_UNDERSTOOD", e.getCode());
    verifyNoInteractions(model);
  }

  @Test
  void matchesNamesWithTrademarkSignsUsingTheTitleHead() {
    Game mw3 = Game.builder().appId(2519060L).name("Call of Duty®: Modern Warfare® III").build();
    when(games.findNameCandidates("call of duty%")).thenReturn(List.of(
        Game.builder().appId(1L).name("Call of Duty®: Black Ops III").build(), mw3));

    assertEquals(Optional.of(mw3), service.findGame("Call of Duty: Modern Warfare III"));
    assertEquals("call of duty: modern warfare iii", AiGameSearchServiceImpl.normalize("  Call of Duty®:  Modern Warfare® III "));
    assertEquals("counter", AiGameSearchServiceImpl.head("counter-strike 2"));
    assertEquals("gtf", AiGameSearchServiceImpl.head("gtf"));
  }

  @Test
  void rejectsBadQueryAndLimitBeforeCallingGpt() {
    assertThrows(InvalidRequestException.class, () -> service.search(" a ", 5));
    assertThrows(InvalidRequestException.class, () -> service.search("가".repeat(201), 5));
    assertThrows(InvalidRequestException.class, () -> service.search("공포 게임", 0));
    assertThrows(InvalidRequestException.class, () -> service.search("공포 게임", 31));
    verifyNoInteractions(keywordClient, model);
  }
}
