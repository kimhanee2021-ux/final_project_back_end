package org.ict.final_project_back_end.domain.aiPc.service;
import java.net.*;import java.net.http.*;import java.time.*;import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.ict.final_project_back_end.domain.aiCommon.exception.AiException;
import org.ict.final_project_back_end.domain.aiPc.dto.PcGameRequirements;
import org.ict.final_project_back_end.domain.aiPc.entity.PcRequirementSnapshot;
import org.ict.final_project_back_end.domain.aiPc.repository.PcRequirementRepository;
import static org.springframework.http.HttpStatus.*;

@Component
public class PcRequirementsClient {
 private final PcRequirementRepository repository;
 private final ObjectMapper json=new ObjectMapper();
 private final HttpClient http;
 @org.springframework.beans.factory.annotation.Autowired
 public PcRequirementsClient(PcRequirementRepository repository){this(repository,HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());}
 public PcRequirementsClient(PcRequirementRepository repository,HttpClient http){this.repository=repository;this.http=http;}
 public record SearchGame(long appId,String name){}
 public java.util.List<SearchGame> search(String keyword){
  if(keyword==null||keyword.isBlank()||keyword.length()>100)return java.util.List.of();
  try{
   var req=HttpRequest.newBuilder(URI.create("https://store.steampowered.com/api/storesearch/?term="+URLEncoder.encode(keyword.trim(),java.nio.charset.StandardCharsets.UTF_8)+"&l=english&cc=kr")).timeout(Duration.ofSeconds(10)).GET().build();
   var res=http.send(req,HttpResponse.BodyHandlers.ofByteArray());if(res.statusCode()!=200||res.body().length>2097152)throw new Exception();
   var result=new java.util.ArrayList<SearchGame>();for(var node:json.readTree(res.body()).path("items")){long id=node.path("id").asLong();String name=node.path("name").asText();if(id>0&&name.length()<=200&&!name.isBlank())result.add(new SearchGame(id,name));if(result.size()==10)break;}return java.util.List.copyOf(result);
  }catch(InterruptedException e){Thread.currentThread().interrupt();throw new AiException(SERVICE_UNAVAILABLE,"검색이 중단됐어요.");}catch(Exception e){throw new AiException(SERVICE_UNAVAILABLE,"Steam 게임 검색을 불러오지 못했어요.");}
 }
 private final Object[] requirementLocks=java.util.stream.IntStream.range(0,64).mapToObj(i->new Object()).toArray();
 public PcGameRequirements get(long id){synchronized(requirementLocks[Math.floorMod(id,requirementLocks.length)]){return load(id);}}
 private PcGameRequirements load(long id){
  if(id<1||id>999999999)throw new AiException(BAD_REQUEST,"올바른 게임을 선택해주세요.");
  var old=repository.findFirstByAppIdOrderByIdDesc(id);
  if(old.isPresent()&&Instant.parse(old.get().data().checkedAt()).isAfter(Instant.now().minusSeconds(86400)))return old.get().data();
  try{
   com.fasterxml.jackson.databind.JsonNode data=null;String sourceRegion="kr";
   // HTTP 접근 거절은 재시도하지 않는다. 성공 응답에 해당 지역 상품 정보만 없을 때 공식 미국 메타데이터를 확인한다.
   for(String region:java.util.List.of("kr","us")){
    var req=HttpRequest.newBuilder(URI.create("https://store.steampowered.com/api/appdetails?appids="+id+"&cc="+region+"&l=english")).timeout(Duration.ofSeconds(10)).GET().build();
    var res=http.send(req,HttpResponse.BodyHandlers.ofByteArray());
    if(res.statusCode()!=200||res.body().length>2097152)throw new Exception();
    var root=json.readTree(res.body()).path(Long.toString(id));
    if(!root.path("success").isBoolean())throw new Exception();
    if(root.path("success").asBoolean()){data=root.path("data");sourceRegion=region;break;}
   }
   if(data==null||data.path("steam_appid").asLong()!=id)throw new Exception();
   if(!"game".equals(data.path("type").asText()))throw new AiException(BAD_REQUEST,"PC 게임 본편을 선택해주세요.");
   var result=new PcGameRequirements(id,data.path("name").asText(),plain(data.path("pc_requirements").path("minimum").asText()),plain(data.path("pc_requirements").path("recommended").asText()),data.path("platforms").path("windows").asBoolean(),"https://store.steampowered.com/app/"+id+"/?cc="+sourceRegion,Instant.now().toString());
   repository.save(new PcRequirementSnapshot(result));return result;
  }catch(AiException e){throw e;}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AiException(SERVICE_UNAVAILABLE,"게임 사양 조회가 중단됐어요.");}catch(Exception e){throw new AiException(SERVICE_UNAVAILABLE,"공식 요구 사양을 확인하지 못했어요. 이전 값을 최신 자료처럼 사용하지 않고 재시도를 기다립니다.");}
 }
 public static String plain(String html){String text=html.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>","").replaceAll("<[^>]+>"," ").replace("&nbsp;"," ").replace("&amp;","&").replace("&lt;","<").replace("&gt;",">").replaceAll("\\s+"," ").trim();return text.substring(0,Math.min(text.length(),15000));}
}
