package org.ict.final_project_back_end.domain.aiPc;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.ict.final_project_back_end.domain.aiPc.service.PcRequirementsClient;
import org.ict.final_project_back_end.domain.aiPc.repository.PcRequirementRepository;
import org.ict.final_project_back_end.domain.aiPc.entity.PcRequirementSnapshot;
import org.ict.final_project_back_end.domain.aiCommon.exception.AiException;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class PcRequirementsClientTest {
 HttpClient http;PcRequirementRepository repository;PcRequirementsClient client;
 @BeforeEach void setup(){http=mock(HttpClient.class);repository=mock(PcRequirementRepository.class);when(repository.findFirstByAppIdOrderByIdDesc(anyLong())).thenReturn(Optional.empty());client=new PcRequirementsClient(repository,http);}
 @SuppressWarnings("unchecked") HttpResponse<byte[]> response(int status,String text){var res=(HttpResponse<byte[]>)mock(HttpResponse.class);when(res.statusCode()).thenReturn(status);when(res.body()).thenReturn(text.getBytes(StandardCharsets.UTF_8));return res;}
 String valid(){return "{\"2357570\":{\"success\":true,\"data\":{\"steam_appid\":2357570,\"type\":\"game\",\"name\":\"Overwatch\",\"platforms\":{\"windows\":true},\"pc_requirements\":{\"minimum\":\"Memory: 6 GB Storage: 50 GB\"}}}}";}
 @Test void regionalMissingMetadataUsesUsOfficialSourceAndPersistsIt()throws Exception{
  var kr=response(200,"{\"2357570\":{\"success\":false}}");var us=response(200,valid());
  when(http.send(any(HttpRequest.class),org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any())).thenReturn(kr,us);
  var data=client.get(2357570);assertTrue(data.source().endsWith("?cc=us"));assertEquals("Overwatch",data.name());
  var saved=ArgumentCaptor.forClass(PcRequirementSnapshot.class);verify(repository).save(saved.capture());assertEquals(data.source(),saved.getValue().data().source());
  var requests=ArgumentCaptor.forClass(HttpRequest.class);verify(http,times(2)).send(requests.capture(),org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());assertTrue(requests.getAllValues().get(0).uri().getQuery().contains("cc=kr"));assertTrue(requests.getAllValues().get(1).uri().getQuery().contains("cc=us"));
 }
 @Test void deniedHttpResponseDoesNotSwitchRegions()throws Exception{
  var denied=response(403,"denied");when(http.send(any(HttpRequest.class),org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any())).thenReturn(denied);assertThrows(AiException.class,()->client.get(2357570));verify(http,times(1)).send(any(HttpRequest.class),org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());verify(repository,never()).save(any());
 }
 @Test void absentEverywhereNeverBecomesInventedRequirements()throws Exception{
  var missing=response(200,"{\"2357570\":{\"success\":false}}");when(http.send(any(HttpRequest.class),org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any())).thenReturn(missing);assertThrows(AiException.class,()->client.get(2357570));verify(repository,never()).save(any());
 }
 @Test void malformedResponseIsNotTreatedAsRegionalAbsence()throws Exception{
  var malformed=response(200,"{}");var us=response(200,valid());when(http.send(any(HttpRequest.class),org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any())).thenReturn(malformed,us);assertThrows(AiException.class,()->client.get(2357570));verify(http,times(1)).send(any(HttpRequest.class),org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
 }
}
