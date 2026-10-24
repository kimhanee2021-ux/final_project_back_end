package org.ict.final_project_back_end.domain.gamenews.service;
import java.time.*;
import java.util.*;
import org.ict.final_project_back_end.domain.gamenews.dto.NewsVoiceIntent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NewsVoiceInterpretationCacheTest {
  @Test void contextHistoryAndExpiryArePartOfReuse() {
    var clock = org.mockito.Mockito.mock(Clock.class);
    org.mockito.Mockito.when(clock.millis()).thenReturn(1000L);
    var cache = new NewsVoiceInterpretationCache(clock);
    var empty = new AiContext(null,null,null,false,null,List.of());
    var selected = new AiContext(570L,"Dota 2",null,false,null,List.of());
    var history = new AiContext(null,null,null,false,null,List.of(new Turn("도타", "SEARCH",570L,"Dota 2",null,false,null)));
    var slots = new Slots("도타", "PATCH",false,false,false,"SEARCH",List.of("Dota 2"));
    cache.put("이 게임 패치",empty,slots);
    assertSame(slots,cache.get("이 게임 패치",empty));
    assertNull(cache.get("이 게임 패치",selected)); assertNull(cache.get("이 게임 패치",history));
    org.mockito.Mockito.when(clock.millis()).thenReturn(301001L);
    assertNull(cache.get("이 게임 패치",empty));
    cache.put("failed",empty,null); assertNull(cache.get("failed",empty));
  }
}
