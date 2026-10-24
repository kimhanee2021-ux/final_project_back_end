package org.ict.final_project_back_end.domain.gamenews;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.ict.final_project_back_end.domain.game.entity.Game;
import org.ict.final_project_back_end.domain.game.repository.GameRepository;
import org.ict.final_project_back_end.domain.gamenews.client.BbsBriefingClient;
import org.ict.final_project_back_end.domain.gamenews.dto.BbsNewsResponse;
import org.ict.final_project_back_end.domain.gamenews.dto.BbsNewsResponse.Article;
import org.ict.final_project_back_end.domain.gamenews.entity.GameNewsCategory;
import org.ict.final_project_back_end.domain.gamenews.exception.BbsBriefingException;
import org.ict.final_project_back_end.domain.gamenews.repository.BbsBriefingRepository;
import org.ict.final_project_back_end.domain.gamenews.service.*;
import org.ict.final_project_back_end.domain.gamenews.service.BriefingContent.Draft;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:bbs-briefing;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "app.bbs.briefing.enabled=true","app.bbs.briefing.daily-calls=1000","app.bbs.briefing.calls-per-minute=1000",
    "app.seed.games.enabled=false","app.seed.prices.enabled=false","app.featured.sync-on-startup=false","app.featured.sync-cron=-"})
@AutoConfigureMockMvc
@Sql("/bbs-briefing-schema.sql")
class BbsBriefingIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired GameRepository games;
  @Autowired BbsBriefingRepository repository;
  @Autowired BbsBriefingService service;
  @MockitoBean BbsOfficialNewsService news;
  @MockitoBean BbsBriefingClient client;
  static final long APP=4200092001L;
  static final String GID="18446744073709551615";
  static final String URL="/api/games/bbs/"+APP+"/news/"+GID+"/briefing";
  Article article;
  Draft draft;
  @BeforeEach void setup() {
    jdbc.update("DELETE FROM game_news_briefings");jdbc.update("DELETE FROM game_news");
    games.saveAndFlush(Game.builder().appId(APP).name("Counter-Strike 2").aboutTheGame("KEEP GAME").build());
    article=article("Updated smoke rendering. Improved map visibility. Fixed audio on the practice map. This official update describes three specific changes for players.");
    draft=new Draft("카운터 스트라이크 2 업데이트 소식",List.of("연막 효과의 표현을 개선했습니다.","맵에서 보이는 시야를 개선했습니다.","연습 맵의 오디오 문제를 수정했습니다."),
        "카운터 스트라이크 2의 공식 업데이트 소식입니다. 이번 소식은 10월 24일 스팀에 게시되었습니다. 먼저 연막 효과의 표현이 개선되었습니다. 연막과 관련한 변경이 이번 업데이트에 포함되어 있다는 내용입니다. 다음으로 맵에서 보이는 시야가 개선되었습니다. 마지막으로 연습 맵에서 발생하던 오디오 문제를 수정했습니다. 이번 공지에서 안내한 핵심은 연막 표현과 맵의 시야 개선, 그리고 연습 맵 오디오 수정이라는 세 가지입니다. 각 변경 사항의 자세한 내용은 스팀 공식 공지에서 확인하실 수 있습니다.");
    BriefingContent.validate(draft);
    when(client.configured()).thenReturn(true);when(client.model()).thenReturn("gpt-5.4-mini");when(client.generate(anyString(),any())).thenReturn(draft);
    snapshot(false);
  }
  Article article(String content) { return new Article(GID,"Official update",content,GameNewsCategory.PATCH,Instant.parse("2026-10-24T01:00:00Z"),"https://steamcommunity.com/games/730/announcements/detail/123","steam_community_announcements"); }
  void snapshot(boolean stale) { when(news.get(APP)).thenReturn(new BbsNewsResponse(APP,Instant.now(),stale,40,List.of(article))); }
  String body() { return "{\"sourceHash\":\""+article.sourceHash()+"\"}"; }
  String postJson(String url,String body) throws Exception { return mvc.perform(post(url).contentType("application/json").content(body)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(); }

  @Test void getNeverGeneratesAndPostPersistsExactThreePointsThenReusesAcrossRequests() throws Exception {
    mvc.perform(get(URL).param("sourceHash",article.sourceHash())).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("EMPTY"));
    verify(client,never()).generate(anyString(),any());assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM game_news",Integer.class));
    mvc.perform(post(URL).contentType("application/json").content(body())).andExpect(status().isOk())
        .andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.status").value("READY"))
        .andExpect(jsonPath("$.briefing.points.length()").value(3)).andExpect(jsonPath("$.briefing.sourceHash").value(article.sourceHash()));
    String saved=jdbc.queryForObject("SELECT gpt_payload FROM game_news_briefings",String.class);
    mvc.perform(get(URL).param("sourceHash",article.sourceHash())).andExpect(jsonPath("$.briefing.titleKo").value(draft.titleKo()));
    postJson(URL,body());verify(client,times(1)).generate(eq("Counter-Strike 2"),eq(article));
    assertEquals(saved,jdbc.queryForObject("SELECT gpt_payload FROM game_news_briefings",String.class));
    assertEquals("KEEP GAME",games.findById(APP).orElseThrow().getAboutTheGame());
    assertTrue(jdbc.queryForObject("SELECT gpt_claim_token IS NULL FROM game_news_briefings",Boolean.class));
  }
  @Test void preservesEveryLegacyColumnAndExistingNewsWhileSavingNewGptFields() throws Exception {
    long id=repository.ensure(APP,article);
    jdbc.update("UPDATE game_news_briefings SET summary='KEEP SUMMARY',ai_content='KEEP CONTENT',model_name='gemini-3.5-flash-lite',generation_status='SUCCEEDED',retry_count=4,prompt_version='old',last_error='KEEP ERROR' WHERE news_id=?",id);
    var beforeNews=jdbc.queryForMap("SELECT * FROM game_news WHERE id=?",id);
    var legacy=jdbc.queryForMap("SELECT summary,ai_content,model_name,generation_status,retry_count,prompt_version,last_error FROM game_news_briefings WHERE news_id=?",id);
    postJson(URL,body());
    assertEquals(beforeNews,jdbc.queryForMap("SELECT * FROM game_news WHERE id=?",id));
    assertEquals(legacy,jdbc.queryForMap("SELECT summary,ai_content,model_name,generation_status,retry_count,prompt_version,last_error FROM game_news_briefings WHERE news_id=?",id));
    assertEquals("gpt-5.4-mini",jdbc.queryForObject("SELECT gpt_model_name FROM game_news_briefings",String.class));
  }
  @Test void changedSourceRejectsOldRequestAndNeverShowsOldSummaryAsCurrent() throws Exception {
    postJson(URL,body());String old=article.sourceHash();String legacyContent=article.content();
    article=article(article.content()+" A new section was added.");snapshot(false);
    mvc.perform(post(URL).contentType("application/json").content("{\"sourceHash\":\""+old+"\"}")).andExpect(status().isConflict());
    mvc.perform(get(URL).param("sourceHash",article.sourceHash())).andExpect(jsonPath("$.status").value("STALE")).andExpect(jsonPath("$.briefing").doesNotExist());
    postJson(URL,body());verify(client,times(2)).generate(anyString(),any());
    assertEquals(legacyContent,jdbc.queryForObject("SELECT content FROM game_news",String.class));
    assertTrue(jdbc.queryForObject("SELECT gpt_payload FROM game_news_briefings",String.class).contains("A new section"));
  }
  @Test void rejectsWrongGameUnknownArticleInvalidHashAndStaleNewsBeforeProvider() throws Exception {
    mvc.perform(post(URL).contentType("application/json").content("{\"sourceHash\":\"wrong\"}")).andExpect(status().isBadRequest());
    mvc.perform(post(URL).contentType("application/json").content("null")).andExpect(status().isBadRequest());
    mvc.perform(get(URL)).andExpect(status().isBadRequest());
    mvc.perform(post(URL.replace(GID,"9876")).contentType("application/json").content(body())).andExpect(status().isNotFound());
    when(news.get(APP+1)).thenReturn(new BbsNewsResponse(APP+1,Instant.now(),false,40,List.of()));
    mvc.perform(post(URL.replace(String.valueOf(APP),String.valueOf(APP+1))).contentType("application/json").content(body())).andExpect(status().isNotFound());
    snapshot(true);mvc.perform(post(URL).contentType("application/json").content(body())).andExpect(status().isServiceUnavailable());
    verify(client,never()).generate(anyString(),any());
  }
  @Test void rejectsInsufficientAndOversizedSourcesAndUnconfiguredProviderWithoutRows() throws Exception {
    article=article("Image only");snapshot(false);
    mvc.perform(post(URL).contentType("application/json").content(body())).andExpect(status().isUnprocessableEntity());
    article=article("x".repeat(24001));snapshot(false);
    mvc.perform(post(URL).contentType("application/json").content(body())).andExpect(status().isUnprocessableEntity());
    when(client.configured()).thenReturn(false);
    mvc.perform(post(URL).contentType("application/json").content(body())).andExpect(status().isServiceUnavailable());
    assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM game_news",Integer.class));verify(client,never()).generate(anyString(),any());
  }
  @Test void invalidOrFailedGenerationNeverReplacesLastSuccessfulPayloadAndReleasesClaim() throws Exception {
    postJson(URL,body());String saved=jdbc.queryForObject("SELECT gpt_payload FROM game_news_briefings",String.class);
    article=article(article.content()+" Changed source.");snapshot(false);
    when(client.generate(anyString(),any())).thenReturn(new Draft("잘못된 결과",List.of("두 개만 반환합니다.","불완전한 결과입니다."),draft.narrationScript()));
    mvc.perform(post(URL).contentType("application/json").content(body())).andExpect(status().isBadGateway());
    assertEquals(saved,jdbc.queryForObject("SELECT gpt_payload FROM game_news_briefings",String.class));
    assertTrue(jdbc.queryForObject("SELECT gpt_claim_token IS NULL FROM game_news_briefings",Boolean.class));
    when(client.generate(anyString(),any())).thenThrow(BbsBriefingException.unavailable());
    mvc.perform(post(URL).contentType("application/json").content(body())).andExpect(status().isBadGateway());
    assertEquals(saved,jdbc.queryForObject("SELECT gpt_payload FROM game_news_briefings",String.class));
  }
  @Test void concurrentRequestUsesDatabaseLeaseAndCallsProviderOnlyOnce() throws Exception {
    var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
    when(client.generate(anyString(),any())).thenAnswer(call -> { entered.countDown();assertTrue(release.await(8,TimeUnit.SECONDS));return draft; });
    try (var pool=Executors.newSingleThreadExecutor()) {
      var first=pool.submit(() -> service.generate(APP,GID,article.sourceHash(),"first"));
      try {
        assertTrue(entered.await(8,TimeUnit.SECONDS));
        assertEquals("PROCESSING",service.get(APP,GID,article.sourceHash()).status());
        assertThrows(BbsBriefingException.class,()->service.generate(APP,GID,article.sourceHash(),"second"));
      } finally { release.countDown(); }
      assertEquals("READY",first.get(8,TimeUnit.SECONDS).status());
    }
    verify(client,times(1)).generate(anyString(),any());
  }
  @Test void expiredLeaseCanBeReclaimedAndOldOwnerCannotOverwriteOrUnlockNewOwner() {
    long id=repository.ensure(APP,article);
    assertTrue(repository.claim(id,"first"));assertFalse(repository.claim(id,"second"));
    jdbc.update("UPDATE game_news_briefings SET gpt_claim_until=TIMESTAMPADD(SECOND,-1,CURRENT_TIMESTAMP) WHERE news_id=?",id);
    assertTrue(repository.claim(id,"second"));
    assertFalse(repository.save(id,"first","bad","old","old"));repository.release(id,"first");
    assertEquals("second",jdbc.queryForObject("SELECT gpt_claim_token FROM game_news_briefings",String.class));
  }
  @Test void savedResultRemainsReadableWithoutAnApiKeyButChangedModelRequiresRegeneration() throws Exception {
    postJson(URL,body());when(client.configured()).thenReturn(false);
    mvc.perform(get(URL).param("sourceHash",article.sourceHash())).andExpect(jsonPath("$.status").value("READY"));
    postJson(URL,body());verify(client,times(1)).generate(anyString(),any());
    when(client.configured()).thenReturn(true);when(client.model()).thenReturn("gpt-other");
    mvc.perform(get(URL).param("sourceHash",article.sourceHash())).andExpect(jsonPath("$.status").value("STALE"));
  }
}
