package org.ict.final_project_back_end.domain.gamenews.service;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.ict.final_project_back_end.domain.gamenews.client.BbsSpeechClient;
import org.ict.final_project_back_end.domain.gamenews.client.BbsSpeechClient.Speech;
import org.ict.final_project_back_end.domain.gamenews.dto.BbsBriefingResponse;
import org.ict.final_project_back_end.domain.gamenews.exception.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BbsAudioServiceTest {
  BbsBriefingService briefings=mock(BbsBriefingService.class);
  BbsSpeechClient client=mock(BbsSpeechClient.class);
  Instant at=Instant.parse("2026-10-24T00:00:00Z");
  String hash="a".repeat(64), script="저장된 낭독 대본입니다. ".repeat(20);
  Speech audio=new Speech(new byte[44],40);
  static class MutableClock extends Clock {
    Instant now=Instant.parse("2026-10-24T00:00:00Z");
    public ZoneId getZone() { return ZoneOffset.UTC; } public Clock withZone(ZoneId zone) { return this; } public Instant instant() { return now; }
  }
  MutableClock clock=new MutableClock();
  BbsAudioService service;
  BbsBriefingResponse ready(String text) { return new BbsBriefingResponse("READY",new BbsBriefingResponse.Briefing("제목",List.of("하나","둘","셋"),text,hash,"gpt-5.4-mini","bbs-ko-2",at,40,"https://steamcommunity.com",at)); }
  @BeforeEach void setup() {
    when(client.configured()).thenReturn(true);when(client.generate(anyString())).thenReturn(audio);
    when(briefings.get(anyLong(),anyString(),anyString())).thenReturn(ready(script));
    service=new BbsAudioService(briefings,client,true,new BbsVoiceRequestGuard(100,100,2),clock);
  }
  Speech generate() { return service.generate(730,"123",hash,at,"local"); }
  @Test void usesOnlySavedScriptCachesReplayAndDoesNotGenerateSummary() {
    assertSame(audio,generate());when(client.configured()).thenReturn(false);assertSame(audio,generate());
    verify(client,times(1)).generate(script);verify(briefings,times(2)).get(730,"123",hash);verifyNoMoreInteractions(briefings);
  }
  @Test void sourceAndVersionMustBeValidBeforeCacheOrProvider() {
    generate();when(briefings.get(anyLong(),anyString(),anyString())).thenThrow(BbsBriefingException.changed());
    assertThrows(BbsBriefingException.class,this::generate);verify(client,times(1)).generate(anyString());
    doReturn(ready(script)).when(briefings).get(anyLong(),anyString(),anyString());
    assertThrows(BbsAudioException.class,() -> service.generate(730,"123",hash,at.plusSeconds(1),"local"));
    when(briefings.get(anyLong(),anyString(),anyString())).thenReturn(BbsBriefingResponse.state("STALE"));
    assertThrows(BbsAudioException.class,this::generate);verify(client,times(1)).generate(anyString());
  }
  @Test void disabledMissingKeyAndNotReadyNeverCallProvider() {
    var disabled=new BbsAudioService(briefings,client,false,100,3,2);
    assertThrows(BbsAudioException.class,() -> disabled.generate(730,"123",hash,at,"local"));verifyNoInteractions(briefings);
    when(client.configured()).thenReturn(false);assertThrows(BbsAudioException.class,this::generate);
    when(client.configured()).thenReturn(true);when(briefings.get(anyLong(),anyString(),anyString())).thenReturn(BbsBriefingResponse.state("EMPTY"));
    assertThrows(BbsAudioException.class,this::generate);verify(client,never()).generate(anyString());
  }
  @Test void cacheExpiresAndIsBoundedToEightArticles() {
    generate();clock.now=clock.now.plusSeconds(1801);generate();verify(client,times(2)).generate(anyString());
    for(int i=0;i<8;i++) service.generate(730,"other"+i,hash,at,"local");
    generate();verify(client,times(11)).generate(anyString());
  }
  @Test void changedScriptDoesNotReuseOldAudioAndFailuresDoNotPoisonCache() {
    generate();when(briefings.get(anyLong(),anyString(),anyString())).thenReturn(ready(script+"변경"));
    when(client.generate(anyString())).thenThrow(BbsAudioException.unavailable());assertThrows(BbsAudioException.class,this::generate);
    doReturn(audio).when(client).generate(anyString());generate();generate();verify(client,times(3)).generate(anyString());
  }
  @Test void simultaneousRequestsForSameArticleDoNotDuplicatePaidCalls() throws Exception {
    var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
    when(client.generate(anyString())).thenAnswer(invocation -> { entered.countDown();assertTrue(release.await(3,TimeUnit.SECONDS));return audio; });
    try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
      var first=executor.submit(this::generate);assertTrue(entered.await(3,TimeUnit.SECONDS));
      try { assertThrows(BbsAudioException.class,this::generate); } finally { release.countDown(); }
      assertSame(audio,first.get(3,TimeUnit.SECONDS));assertSame(audio,generate());verify(client,times(1)).generate(anyString());
    }
  }
  @Test void failedGenerationStillConsumesBudgetWithoutAutomaticRetry() {
    service=new BbsAudioService(briefings,client,true,1,100,2);
    when(client.generate(anyString())).thenThrow(BbsAudioException.unavailable());
    assertThrows(BbsAudioException.class,this::generate);assertThrows(BbsAudioException.class,this::generate);
    verify(client,times(1)).generate(anyString());
  }
}
