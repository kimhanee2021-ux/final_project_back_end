package org.ict.final_project_back_end.domain.aiManager;
import org.ict.final_project_back_end.domain.aiManager.dto.CollectionDraft;
import org.ict.final_project_back_end.domain.aiCommon.exception.AiException;
import org.ict.final_project_back_end.domain.aiManager.service.ManagerStore;
import org.ict.final_project_back_end.domain.aiManager.service.ManagerAiService;
import org.ict.final_project_back_end.domain.aiManager.service.ManagerAiClient;
import org.ict.final_project_back_end.domain.aiManager.service.SteamProductClient;
import org.ict.final_project_back_end.domain.aiManager.repository.ManagerRepository;
import org.ict.final_project_back_end.domain.aiManager.dto.ProductPrice;
import org.junit.jupiter.api.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.ict.final_project_back_end.domain.user.entity.User;
import org.ict.final_project_back_end.domain.user.repository.UserRepository;
import org.ict.final_project_back_end.global.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties="app.seed.games.enabled=false") @AutoConfigureMockMvc @Transactional
class ManagerApiTest {
 @Autowired ManagerStore store;@Autowired ManagerRepository repo;@Autowired UserRepository users;@Autowired MockMvc mvc;@Autowired JwtService jwt;
 @MockitoBean SteamProductClient steam;@MockitoBean ManagerAiClient client;@Autowired ManagerAiService ai;
 Long user,other;String token;
 @BeforeEach void setup(){user=account();other=account();token="Bearer "+jwt.generateToken(user,"test@example.invalid");when(steam.product(105600)).thenReturn(new ProductPrice(105600,"Terraria","game",null,"KRW",550000L,"",List.of(),"now"));when(steam.product(620)).thenReturn(new ProductPrice(620,"Portal 2","game",null,"KRW",1100000L,"",List.of(),"now"));}
 Long account(){return users.save(User.builder().email(UUID.randomUUID()+"@example.invalid").nickname(UUID.randomUUID().toString()).passwordHash("test").build()).getId();}
 @Test void authenticationRequired()throws Exception{mvc.perform(get("/api/ai/manager")).andExpect(status().isUnauthorized());}
 @Test void stateSerializesStoredPayloadAsActualData()throws Exception{store.addOwned(user,105600);store.collection(user,new CollectionDraft("탐험","reason",List.of(105600L)));mvc.perform(get("/api/ai/manager").header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.records[0].data.appIds[0]").value(105600)).andExpect(jsonPath("$.records[0].data.name").value("탐험"));}
 @Test void reactivationAllowsOneNewNotification(){store.saveWatch(user,null,List.of(620L),12000,"Portal");var r=repo.findByUserIdAndKind(user,"watch").getFirst();store.pause(user,r.id,false);store.pause(user,r.id,true);assertEquals("",store.watch(r).lastChecked());store.check(user,r.id);store.check(user,r.id);assertEquals(2,repo.findByUserIdAndKind(user,"notification").size());}
 @Test void unchangedEditDoesNotCreateAnotherNotification(){store.saveWatch(user,null,List.of(620L),12000,"Portal");var r=repo.findByUserIdAndKind(user,"watch").getFirst();store.saveWatch(user,r.id,List.of(620L),12000,"Portal");assertEquals(1,repo.findByUserIdAndKind(user,"notification").size());}
 @Test void identicalCreationRetryReturnsSavedRecord(){store.saveWatch(user,null,List.of(620L),12000,"Portal");store.saveWatch(user,null,List.of(620L),12000,"Portal");assertEquals(1,repo.findByUserIdAndKind(user,"watch").size());assertEquals(1,repo.findByUserIdAndKind(user,"notification").size());store.addOwned(user,105600);var draft=new CollectionDraft("탐험","reason",List.of(105600L));store.collection(user,draft);store.collection(user,draft);assertEquals(1,repo.findByUserIdAndKind(user,"collection").size());assertFalse(store.owned(user).getFirst().containsKey("minutes"));}
 @Test void foreignOwnerCannotReadRenameOrDelete(){store.addOwned(user,105600);Long id=repo.findByUserIdAndKind(user,"owned").getFirst().id;assertThrows(AiException.class,()->store.delete(other,id));assertThrows(AiException.class,()->store.rename(other,id,"name"));assertEquals(1,repo.findByUserIdAndKind(user,"owned").size());}
 @Test void collectionMustUseOwnedAndSurvivesReload(){assertThrows(AiException.class,()->store.collection(user,new CollectionDraft("test","reason",List.of(105600L))));store.addOwned(user,105600);store.collection(user,new CollectionDraft("탐험","실제 보유",List.of(105600L)));assertEquals(1,repo.findByUserIdAndKind(user,"collection").size());assertEquals(1,store.owned(user).size());}
 @Test void noDuplicateNotificationsOnRepeatedMatchingCheck(){store.saveWatch(user,null,List.of(620L),12000,"Portal 기다리기");Long id=repo.findByUserIdAndKind(user,"watch").getFirst().id;store.check(user,id);store.check(user,id);assertEquals(1,repo.findByUserIdAndKind(user,"notification").size());}
 @Test void failedPriceFetchPreservesPreviousCondition(){store.saveWatch(user,null,List.of(620L),12000,"Portal");var r=repo.findByUserIdAndKind(user,"watch").getFirst();when(steam.product(620)).thenThrow(new AiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"조회 실패"));store.check(user,r.id);var w=store.watch(repo.findById(r.id).orElseThrow());assertTrue(w.matched());assertEquals(1100000L,w.totalMinor());assertEquals("조회 실패",w.error());assertEquals(1,repo.findByUserIdAndKind(user,"notification").size());}
 @Test void ownedProductsAreExcludedFromPrice(){store.addOwned(user,105600);store.saveWatch(user,null,List.of(105600L,620L),12000,"구매");var w=store.watch(repo.findByUserIdAndKind(user,"watch").getFirst());assertEquals(1100000L,w.totalMinor());assertThrows(AiException.class,()->store.saveWatch(user,null,List.of(105600L),12000,"중복"));}
 @Test void hallucinatedAiIdIsRejected()throws Exception{store.addOwned(user,105600);when(client.draft(anyString(),anyString(),anyList())).thenReturn(new ObjectMapper().readTree("{\"name\":\"test\",\"explanation\":\"reason\",\"appIds\":[999],\"targetWon\":0}"));assertThrows(AiException.class,()->ai.draft(user,"collection","정리해줘"));}
 @Test void invalidCurrencyAndMissingParentCannotBeSaved(){when(steam.product(2)).thenReturn(new ProductPrice(2,"DLC","dlc",1L,"KRW",100L,"",List.of(),"now"));assertThrows(AiException.class,()->store.saveWatch(user,null,List.of(2L),1000,"DLC"));}
 @Test void readNotificationDoesNotChangeOtherUser(){store.saveWatch(user,null,List.of(620L),12000,"Portal");var n=repo.findByUserIdAndKind(user,"notification").getFirst();assertThrows(AiException.class,()->store.read(other,n.id));store.read(user,n.id);assertTrue(repo.findById(n.id).orElseThrow().payload.contains("\"read\":true"));}
}
