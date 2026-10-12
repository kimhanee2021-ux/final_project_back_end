package org.ict.final_project_back_end.domain.manager;
import java.util.*;
import org.springframework.stereotype.Service;
import org.ict.final_project_back_end.domain.ai.*;
import static org.springframework.http.HttpStatus.*;
@Service
public class ManagerAiService {
 public record Draft(String name,String explanation,List<Long> appIds,long targetWon){}
 private final ManagerStore store;private final ManagerAiClient client;private final WorldcupCatalog catalog;private final AiRateLimit limit;
 public ManagerAiService(ManagerStore s,ManagerAiClient c,WorldcupCatalog cat,AiRateLimit l){store=s;client=c;catalog=cat;limit=l;}
 public Draft draft(Long user,String mode,String message){store.auth(user);if(!Set.of("collection","purchase").contains(mode)||message==null||message.isBlank()||message.length()>500)throw new AiException(BAD_REQUEST,"요청을 500자 이내로 적어주세요.");
  List<Map<String,Object>> games;Set<Long> allowed=new HashSet<>();
  if(mode.equals("collection")){games=store.owned(user).stream().map(g->{long id=((Number)g.get("appId")).longValue();allowed.add(id);var c=catalog.game(id);var item=new LinkedHashMap<String,Object>(g);if(c!=null){item.put("tags",c.tags());item.put("description",c.description());}return (Map<String,Object>)item;}).limit(100).toList();allowed.clear();games.forEach(g->allowed.add(((Number)g.get("appId")).longValue()));if(games.isEmpty())throw new AiException(BAD_REQUEST,"보유 게임을 동기화하거나 직접 등록해 주세요.");}
  else{games=catalog.games().stream().map(g->{allowed.add(g.appId());return Map.<String,Object>of("appId",g.appId(),"name",g.name(),"tags",g.tags());}).toList();}
  limit.beginManager(user);try{var r=client.draft(mode,message,games);com.fasterxml.jackson.databind.JsonNode name=r.path("name"),explanation=r.path("explanation"),ids=r.path("appIds"),target=r.path("targetWon");if(!name.isTextual()||name.asText().isBlank()||name.asText().length()>50||!explanation.isTextual()||explanation.asText().isBlank()||explanation.asText().length()>500||!ids.isArray()||ids.size()>(mode.equals("collection")?100:6)||!target.isIntegralNumber()||target.asLong()<0||target.asLong()>10000000)throw invalid();var selected=new LinkedHashSet<Long>();for(var id:ids){if(!id.isIntegralNumber()||!allowed.contains(id.asLong())||!selected.add(id.asLong()))throw invalid();}return new Draft(name.asText(),explanation.asText(),List.copyOf(selected),mode.equals("collection")?0:target.asLong());}finally{limit.end(user);}
 }
 private AiException invalid(){return new AiException(BAD_GATEWAY,"AI 초안에 확인되지 않은 정보가 있어요. 다시 시도해주세요.");}
}
