package org.ict.final_project_back_end.domain.gamenews.service;

import java.time.Instant;
import java.util.*;
import org.ict.final_project_back_end.domain.game.dto.BbsGameResponse;
import org.ict.final_project_back_end.domain.game.service.BbsGameSearchService;
import org.ict.final_project_back_end.domain.gamenews.client.BbsBriefingClient;
import org.ict.final_project_back_end.domain.gamenews.dto.BbsNewsResponse;
import org.ict.final_project_back_end.domain.gamenews.dto.BbsNewsResponse.Article;
import org.ict.final_project_back_end.domain.gamenews.entity.GameNewsCategory;
import org.ict.final_project_back_end.domain.gamenews.exception.BbsBriefingException;
import org.ict.final_project_back_end.domain.gamenews.repository.BbsBriefingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BbsBriefingSafetyTest {
  BbsOfficialNewsService news=mock(BbsOfficialNewsService.class);
  BbsGameSearchService games=mock(BbsGameSearchService.class);
  BbsBriefingRepository repository=mock(BbsBriefingRepository.class);
  BbsBriefingClient client=mock(BbsBriefingClient.class);
  Article article=new Article("123","Official announcement","The update improves smoke rendering, adjusts map visibility and fixes practice audio. These three changes are now available.",GameNewsCategory.PATCH,Instant.EPOCH,"https://steamcommunity.com/news/123","steam_community_announcements");
  BbsBriefingService service(boolean enabled) {
    when(news.get(730)).thenReturn(new BbsNewsResponse(730,Instant.now(),false,40,List.of(article)));
    when(client.configured()).thenReturn(true);when(client.model()).thenReturn("gpt-test");
    return new BbsBriefingService(news,games,repository,client,enabled,1,1,1);
  }
  @Test void disabledFeatureCannotTouchDatabaseOrCallAnyProvider() {
    var service=service(false);
    assertEquals("UNAVAILABLE",service.get(730,"123",article.sourceHash()).status());
    assertEquals(503,assertThrows(BbsBriefingException.class,()->service.generate(730,"123",article.sourceHash(),"ip")).getStatus().value());
    verifyNoInteractions(repository,games);verify(news,never()).get(anyLong());verify(client,never()).generate(anyString(),any());
  }
  @Test void unavailableSchemaStopsBeforePaidProviderCall() {
    var service=service(true);
    when(repository.find(anyLong(),anyString())).thenThrow(new DataAccessResourceFailureException("private DB diagnostic"));
    var error=assertThrows(BbsBriefingException.class,()->service.generate(730,"123",article.sourceHash(),"ip"));
    assertEquals("BBS_BRIEFING_STORAGE",error.getCode());assertFalse(error.getMessage().contains("private"));
    verify(client,never()).generate(anyString(),any());verify(repository,never()).ensure(anyLong(),any());
  }
  @Test void failedCallsConsumeBudgetAndNeverAutomaticallyRetry() {
    var service=service(true);
    when(repository.find(anyLong(),anyString())).thenReturn(Optional.empty());
    when(games.find(730)).thenReturn(new BbsGameResponse(730,"CS2",null,null));
    when(repository.ensure(anyLong(),any())).thenReturn(1L);when(repository.claim(anyLong(),anyString())).thenReturn(true);
    when(client.generate(anyString(),any())).thenThrow(BbsBriefingException.unavailable());
    assertEquals(502,assertThrows(BbsBriefingException.class,()->service.generate(730,"123",article.sourceHash(),"ip")).getStatus().value());
    assertEquals(429,assertThrows(BbsBriefingException.class,()->service.generate(730,"123",article.sourceHash(),"ip")).getStatus().value());
    verify(client,times(1)).generate(anyString(),any());verify(repository).release(eq(1L),anyString());
  }
}
