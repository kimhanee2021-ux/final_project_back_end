package org.ict.final_project_back_end.domain.ai;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.ict.final_project_back_end.domain.user.repository.UserRepository;

class WorldcupServiceTest {
  WorldcupCatalog catalog=new WorldcupCatalog();
  GeminiClient client=mock(GeminiClient.class);
  UserRepository users=mock(UserRepository.class);
  WorldcupService service=new WorldcupService(catalog,client,new AiRateLimit(),users);
  ObjectMapper json=new ObjectMapper();
  @BeforeEach void setup(){when(users.existsById(1L)).thenReturn(true);}
  @Test void unknownUserNeverCallsGemini(){assertThrows(AiException.class,()->service.next(null,new WorldcupRequest("test",10,List.of(),List.of())));verifyNoInteractions(client);}
  @Test void invalidGroupsAndInsufficientCatalogFailBeforeGemini(){
    assertThrows(AiException.class,()->service.next(1L,new WorldcupRequest("test",10,List.of("invalid"),List.of())));
    var small=catalog.groups().stream().filter(g->g.count()<20).findFirst().orElseThrow();
    assertThrows(AiException.class,()->service.next(1L,new WorldcupRequest("test",10,List.of(small.id()),List.of())));verifyNoInteractions(client);
  }
  @Test void validPairComesFromSuppliedCandidates() {
    when(client.generateWorldcup(anyMap())).thenAnswer(invocation->{Map<String,Object> payload=invocation.getArgument(0);@SuppressWarnings("unchecked") var pool=(List<WorldcupGame>)payload.get("candidates");return json.readTree("{\"explanation\":\"분위기를 비교해요\",\"pairIds\":["+pool.get(0).appId()+","+pool.get(1).appId()+"],\"summary\":\"\",\"traits\":[],\"evidence\":[],\"uncertainty\":\"\",\"favoriteIds\":[]}");});
    var result=service.next(1L,new WorldcupRequest("test",3,List.of(),List.of()));assertEquals(2,result.pair().size());assertFalse(result.complete());
  }
  @Test void fabricatedAndDuplicatePairsFail() throws Exception {
    when(client.generateWorldcup(anyMap())).thenReturn(json.readTree("{\"explanation\":\"비교\",\"pairIds\":[0,0],\"summary\":\"\",\"traits\":[],\"evidence\":[],\"uncertainty\":\"\",\"favoriteIds\":[]}"));
    assertThrows(AiException.class,()->service.next(1L,new WorldcupRequest("test",10,List.of(),List.of())));
  }
  @Test void nextPairCannotBeMistakenForFinalResult() {
    when(client.generateWorldcup(anyMap())).thenAnswer(invocation->{Map<String,Object> payload=invocation.getArgument(0);@SuppressWarnings("unchecked") var pool=(List<WorldcupGame>)payload.get("candidates");return json.readTree("{\"explanation\":\"마지막 두 게임을 비교해요\",\"pairIds\":["+pool.get(0).appId()+","+pool.get(1).appId()+"]}");});
    var games=catalog.games();var history=new ArrayList<WorldcupRequest.Choice>();for(int i=0;i<18;i+=2)history.add(new WorldcupRequest.Choice(games.get(i).appId(),games.get(i+1).appId(),"left",List.of(),""));
    var result=service.next(1L,new WorldcupRequest("test",10,List.of(),history));assertFalse(result.complete());assertEquals(2,result.pair().size());assertTrue(result.summary().isEmpty());
  }
  @Test void allUnknownProducesResultWithoutInventedFavorite() throws Exception {
    var games=catalog.games();var history=new ArrayList<WorldcupRequest.Choice>();
    for(int i=0;i<20;i+=2)history.add(new WorldcupRequest.Choice(games.get(i).appId(),games.get(i+1).appId(),"unknown",List.of(),""));
    when(client.generateWorldcup(anyMap())).thenReturn(json.readTree("{\"explanation\":\"\",\"pairIds\":[],\"summary\":\"선호를 판단하기 어려워요\",\"traits\":[],\"evidence\":[],\"uncertainty\":\"모름 선택만 있어요\",\"favoriteIds\":[]}"));
    var result=service.next(1L,new WorldcupRequest("test",10,List.of(),history));assertTrue(result.complete());assertTrue(result.favorites().isEmpty());
    when(client.generateWorldcup(anyMap())).thenReturn(json.readTree("{\"explanation\":\"\",\"pairIds\":[],\"summary\":\"결과\",\"traits\":[],\"evidence\":[],\"uncertainty\":\"\",\"favoriteIds\":["+games.get(0).appId()+"]}"));
    assertThrows(AiException.class,()->service.next(1L,new WorldcupRequest("test",10,List.of(),history)));
  }
}
