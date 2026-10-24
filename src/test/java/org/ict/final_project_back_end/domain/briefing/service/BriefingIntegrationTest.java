package org.ict.final_project_back_end.domain.briefing.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.ict.final_project_back_end.domain.briefing.repository.*;
import org.ict.final_project_back_end.domain.game.entity.Game;
import org.ict.final_project_back_end.domain.game.repository.GameRepository;
import org.ict.final_project_back_end.global.security.JwtService;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class BriefingIntegrationTest {
  @Autowired OfficialNewsRepository news;
  @Autowired GameBriefingRepository briefings;
  @Autowired BriefingCheckpointRepository checkpoints;
  @Autowired BriefingCatalogRepository catalog;
  @Autowired GameRepository games;
  @Autowired MockMvc mvc;
  @Autowired JwtService jwt;
  @MockitoBean SteamOfficialNewsClient steam;
  @MockitoBean BriefingAiClient ai;
  private BriefingWorker worker(int limit) {return new BriefingWorker(catalog,news,briefings,checkpoints,steam,ai,true,true,1,limit);}
  private String token(){return "Bearer "+jwt.generateToken(123456L,"briefing@example.invalid");}
  private void approvedSource(long appId,String gid,String name){
    var source=new org.ict.final_project_back_end.domain.briefing.entity.OfficialNews(appId,name,gid);
    source.update("원문","설명","https://steamcommunity.com/games/"+appId+"/announcements/"+gid,Instant.parse("2026-10-20T00:00:00Z"),Instant.now());
    news.saveAndFlush(source);
  }
  @Test void catalogScanDeduplicatesAndReusesAnalysisUntilSourceChanges() throws Exception {
    games.saveAndFlush(Game.builder().appId(991199L).name("검증 게임").build());
    approvedSource(991199L,"101","검증 게임");
    when(steam.fetch(991199L)).thenReturn(List.of(new SteamOfficialNewsClient.Source("101","수정 공지","문제 수정 발표","https://steamcommunity.com/games/991199/announcements/101",Instant.parse("2026-10-20T00:00:00Z"))));
    when(ai.configured()).thenReturn(true);
    when(ai.analyze(anyList())).thenReturn(new BriefingAiClient.Analysis(List.of(new BriefingContent.Item("CHANGE","수정 발표","원문에서 수정이 발표됐어요.",List.of("101"),"FIX")),List.of(new BriefingAiClient.Translation("101","수정 공지","수정 내용이 발표됐어요."))));
    var worker=worker(2);
    worker.tick();assertEquals(1,news.count());assertEquals(1,briefings.count());assertEquals("수정 공지",news.findByAppIdAndGid(991199L,"101").orElseThrow().getKoreanTitle());
    worker.tick();worker.tick();
    assertEquals(1,news.count());verify(ai,times(1)).analyze(anyList());
    mvc.perform(get("/api/board/briefing").header("Authorization",token()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].items[0].sourceIds[0]").value("101"));
    var row=news.findByAppIdAndGid(991199L,"101").orElseThrow();
    row.update(row.getTitle(),"수정한 본문",row.getUrl(),row.getPublishedAt(),Instant.now());news.saveAndFlush(row);
    mvc.perform(get("/api/board/briefing").header("Authorization",token()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].stale").value(true)).andExpect(jsonPath("$.items[0].items").isEmpty());
  }
  @Test void quotaZeroStillCollectsWithoutCallingAi() throws Exception {
    games.saveAndFlush(Game.builder().appId(991198L).name("예산 검증").build());
    approvedSource(991198L,"102","예산 검증");
    when(steam.fetch(991198L)).thenReturn(List.of(new SteamOfficialNewsClient.Source("102","행사","행사 설명","https://steamcommunity.com/games/991198/announcements/102",Instant.now())));
    when(ai.configured()).thenReturn(true);
    worker(0).tick();assertEquals(1,news.count());assertEquals(0,briefings.count());verify(ai,never()).analyze(anyList());
  }
  @Test void neverScansUnapprovedCatalogGames() {
    games.saveAndFlush(Game.builder().appId(999991L).name("수집범위밖").build());
    worker(0).tick();verifyNoInteractions(steam);
  }
  @Test void readsRequireAuthenticationAndCannotStartCollection() throws Exception {
    mvc.perform(get("/api/board/briefing")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/board/official-news")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/board/official-news?page=-1").header("Authorization",token())).andExpect(status().isBadRequest());
    mvc.perform(get("/api/board/briefing").header("Authorization",token())).andExpect(status().isOk());
    verifyNoInteractions(steam);verify(ai,never()).analyze(anyList());
  }
}
