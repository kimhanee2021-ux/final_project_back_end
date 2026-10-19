package org.ict.final_project_back_end.domain.aiPc.service;
import org.springframework.stereotype.Service;
import org.ict.final_project_back_end.domain.aiPc.dto.PcPart;
import org.ict.final_project_back_end.domain.aiPc.entity.PcPartPriceSnapshot;
import org.ict.final_project_back_end.domain.aiPc.repository.PcPartPriceRepository;
import org.ict.final_project_back_end.domain.user.repository.UserRepository;
import org.ict.final_project_back_end.domain.aiCommon.exception.AiException;
import java.net.URI;import java.net.http.*;import java.nio.charset.StandardCharsets;import java.time.*;import java.util.*;
@Service
public class PcPartsService {
 private final PcPartPriceRepository prices;private final UserRepository users;
 private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
 public PcPartsService(PcPartPriceRepository p,UserRepository u){prices=p;users=u;}
 public List<PcPart.Offer> offers(Long userId){if(userId==null||!users.existsById(userId))throw new AiException(org.springframework.http.HttpStatus.UNAUTHORIZED,"로그인 후 부품을 확인해주세요.");return collect();}
 // 허용된 카탈로그 URL만 조회한다. 사용자/AI URL을 요청하지 않는다. 6시간 내에는 외부 요청 없이 저장값을 사용한다.
 private synchronized List<PcPart.Offer> collect(){var result=new ArrayList<PcPart.Offer>();for(var part:PcPartsCatalog.PARTS){var history=prices.findTop2ByProductCodeAndSourceKeyOrderByIdDesc(part.productCode(),"DANAWA");PcPartPriceSnapshot current=history.isEmpty()?null:history.get(0),previous=history.size()>1?history.get(1):null;
   if(current==null||Instant.parse(current.at()).isBefore(Instant.now().minusSeconds(21600))){previous=current;current=fetch(part);prices.save(current);}
   Long previousPrice=previous==null?null:previous.price();result.add(PcPartOffers.withRetailFallback(new PcPart.Offer(part,current.price(),current.status(),current.note(),current.at(),previousPrice,previous==null?null:previous.at(),PcPartPrices.dropPercent(previousPrice,current.price()),retailQuotes(part))));
  }return List.copyOf(result);}
 private List<PcPart.RetailQuote> retailQuotes(PcPart part){
  String url=PcRetailSources.comtrixUrl(part.productCode());
  if(url==null)return List.of();
  var history=prices.findTop2ByProductCodeAndSourceKeyOrderByIdDesc(part.productCode(),"COMTRIX");
  var current=history.isEmpty()?null:history.getFirst();
  if(current==null||Instant.parse(current.at()).isBefore(Instant.now().minusSeconds(21600))){
   String at=Instant.now().toString();PcPartPrices.Parsed parsed;
   try{
    var request=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8)).header("User-Agent","GameSense-PC-Research/1.0").GET().build();
    var response=http.send(request,HttpResponse.BodyHandlers.ofInputStream());
    try(var stream=response.body()){byte[] body=stream.readNBytes(2097153);if(response.statusCode()!=200||body.length>2097152)throw new IllegalStateException();parsed=PcRetailPrices.parseComtrix(new String(body,StandardCharsets.UTF_8),part.name());}
   }catch(InterruptedException e){Thread.currentThread().interrupt();parsed=new PcPartPrices.Parsed(null,"FAILED","판매처 조회가 중단됐어요.");}
   catch(Exception e){parsed=new PcPartPrices.Parsed(null,"FAILED","판매처 가격을 확인하지 못했어요.");}
   current=prices.save(new PcPartPriceSnapshot(part.productCode(),"COMTRIX",parsed.price(),parsed.status(),parsed.note(),at));
  }
  return List.of(new PcPart.RetailQuote("COMTRIX",url,current.price(),current.status(),current.note(),current.at()));
 }
 private PcPartPriceSnapshot fetch(PcPart part){String at=Instant.now().toString();try{
   var request=HttpRequest.newBuilder(URI.create(part.priceSource())).timeout(Duration.ofSeconds(8)).header("User-Agent","GameSense-PC-Research/1.0").GET().build();
   var response=http.send(request,HttpResponse.BodyHandlers.ofInputStream());
   try(var stream=response.body()){byte[] body=stream.readNBytes(2097153);if(response.statusCode()!=200||body.length>2097152)throw new IllegalStateException();var value=PcPartPrices.parse(new String(body,StandardCharsets.UTF_8),part.name());return new PcPartPriceSnapshot(part.productCode(),value.price(),value.status(),value.note(),at);}
  }catch(InterruptedException e){Thread.currentThread().interrupt();return new PcPartPriceSnapshot(part.productCode(),null,"FAILED","조회가 중단됐어요. 원문에서 확인해 주세요.",at);}catch(Exception e){return new PcPartPriceSnapshot(part.productCode(),null,"FAILED","현재 가격을 확인하지 못했어요. 원문에서 확인해 주세요.",at);}}
}
