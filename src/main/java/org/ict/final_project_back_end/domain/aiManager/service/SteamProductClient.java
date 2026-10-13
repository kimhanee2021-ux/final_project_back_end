package org.ict.final_project_back_end.domain.aiManager.service;
import org.ict.final_project_back_end.domain.aiCommon.exception.AiException;
import org.ict.final_project_back_end.domain.aiManager.dto.ProductPrice;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;
import static org.springframework.http.HttpStatus.*;
@Component
public class SteamProductClient {
 private record Cache(ProductPrice data,Instant at){}
 private final Map<Long,Cache> cache=new java.util.concurrent.ConcurrentHashMap<>();
 private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
 private final ObjectMapper json=new ObjectMapper();
 public ProductPrice product(long id){
  if(id<1||id>999999999)throw new AiException(BAD_REQUEST,"올바른 Steam 게임 번호를 입력해주세요.");
  var old=cache.get(id);if(old!=null&&old.at().isAfter(Instant.now().minusSeconds(300)))return old.data();
  try{
   var req=HttpRequest.newBuilder(URI.create("https://store.steampowered.com/api/appdetails?appids="+id+"&cc=kr&l=koreana")).timeout(Duration.ofSeconds(8)).GET().build();
   var res=http.send(req,HttpResponse.BodyHandlers.ofByteArray());if(res.statusCode()!=200||res.body().length>2097152)throw new Exception();
   var root=json.readTree(res.body()).path(Long.toString(id));if(!root.path("success").asBoolean())throw new Exception();var d=root.path("data");
   String type=d.path("type").asText();if(!Set.of("game","dlc").contains(type))throw new AiException(BAD_REQUEST,"본편 또는 DLC를 선택해주세요. 사운드트랙 등은 지원하지 않아요.");
   var price=d.path("price_overview");Long minor=price.path("final").isIntegralNumber()?price.path("final").asLong():d.path("is_free").asBoolean()?0L:null;
   var dlcs=new ArrayList<Long>();for(var n:d.path("dlc"))if(n.isIntegralNumber())dlcs.add(n.asLong());
   String parent=d.path("fullgame").path("appid").asText();Long parentId=parent.matches("[0-9]+")?Long.valueOf(parent):null;
   var result=new ProductPrice(id,d.path("name").asText(),type,parentId,d.path("is_free").asBoolean()&&price.path("currency").asText().isBlank()?"KRW":price.path("currency").asText(),minor,d.path("header_image").asText(),List.copyOf(dlcs),Instant.now().toString());
   if(cache.size()>1000)cache.clear();cache.put(id,new Cache(result,Instant.now()));return result;
  }catch(AiException e){throw e;}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AiException(SERVICE_UNAVAILABLE,"상품 확인이 중단됐어요. 다시 시도해주세요.");}catch(Exception e){throw new AiException(SERVICE_UNAVAILABLE,"Steam 상품 정보를 확인하지 못했어요. 잠시 후 다시 시도해주세요.");}
 }
}
