package org.ict.final_project_back_end.domain.ai;
import java.util.*;
import org.springframework.stereotype.Service;
import org.ict.final_project_back_end.domain.user.repository.UserRepository;
import static org.springframework.http.HttpStatus.*;

@Service
public class WorldcupService {
  private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(WorldcupService.class);
  public record Result(String explanation,List<WorldcupGame> pair,boolean complete,String summary,List<String> traits,List<String> evidence,String uncertainty,List<WorldcupGame> favorites) {}
  private final WorldcupCatalog catalog;private final GeminiClient gemini;private final AiRateLimit limit;private final UserRepository users;
  public WorldcupService(WorldcupCatalog c,GeminiClient g,AiRateLimit l,UserRepository u){catalog=c;gemini=g;limit=l;users=u;}
  public void authenticated(Long id){if(id==null || !users.existsById(id))throw new AiException(UNAUTHORIZED,"로그인이 필요합니다.");}
  public static void validateHistory(List<WorldcupRequest.Choice> history,WorldcupCatalog catalog){
    Set<Long> seen=new HashSet<>();for(var c:history){
      if(c==null || c.leftId()==c.rightId() || catalog.game(c.leftId())==null || catalog.game(c.rightId())==null || !seen.add(c.leftId()) || !seen.add(c.rightId()) || !Set.of("left","right","both","neither","unknown").contains(c.verdict()))throw new AiException(BAD_REQUEST,"선택 기록을 확인해주세요.");
    }
  }
  public Result next(Long userId,WorldcupRequest request){
    authenticated(userId);validateHistory(request.history(),catalog);
    if(!Set.of(3,5,10,16,24).contains(request.rounds()) || request.history().size()>request.rounds())throw new AiException(BAD_REQUEST,"진행 횟수를 확인해주세요.");
    var allowedGroups=catalog.groups().stream().map(WorldcupCatalog.Group::id).toList();
    if(!allowedGroups.containsAll(request.groups()))throw new AiException(BAD_REQUEST,"게임 분류를 확인해주세요.");
    var filtered=catalog.filtered(request.groups());
    if(filtered.size()<request.rounds()*2)throw new AiException(BAD_REQUEST,"선택한 분류의 게임이 부족합니다. 분류를 더 고르거나 대결 횟수를 줄여주세요.");
    var allowed=filtered.stream().map(WorldcupGame::appId).toList();
    if(request.history().stream().anyMatch(c->!allowed.contains(c.leftId())||!allowed.contains(c.rightId())))throw new AiException(BAD_REQUEST,"분류와 선택 기록이 일치하지 않습니다.");
    boolean complete=request.history().size()==request.rounds();
    var pool=complete?List.<WorldcupGame>of():catalog.pool(request.groups(),request.history(),request.sessionId());
    var history=request.history().stream().map(c->Map.of("left",catalog.game(c.leftId()),"right",catalog.game(c.rightId()),"verdict",c.verdict(),"reasons",c.reasons(),"note",c.note())).toList();
    var payload=Map.of("complete",complete,"round",request.history().size()+1,"totalRounds",request.rounds(),"history",history,"candidates",pool);
    limit.beginWorldcup(userId);
    try {
      var response=gemini.generateWorldcup(payload);
      String explanation=complete?"":string(response,"explanation",600,false),summary=complete?string(response,"summary",1200,false):"",uncertainty=complete?string(response,"uncertainty",600,true):"";
      List<String> traits=complete?list(response,"traits",6):List.of(),evidence=complete?list(response,"evidence",6):List.of();
      List<WorldcupGame> pair=new ArrayList<>();var ids=complete?com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode():response.path("pairIds");
      if(!ids.isArray() || ids.size()!=(complete?0:2))throw invalid();
      for(var id:ids){if(!id.isIntegralNumber())throw invalid();var game=pool.stream().filter(g->g.appId()==id.asLong()).findFirst().orElseThrow(WorldcupService::invalid);if(pair.contains(game))throw invalid();pair.add(game);}
      if(!complete)return new Result(explanation,List.copyOf(pair),false,"",List.of(),List.of(),"",List.of());
      var selected=new LinkedHashSet<Long>();for(var c:request.history()){if(c.verdict().equals("left")||c.verdict().equals("both"))selected.add(c.leftId());if(c.verdict().equals("right")||c.verdict().equals("both"))selected.add(c.rightId());}
      var favorites=new ArrayList<WorldcupGame>();var favoriteIds=response.path("favoriteIds");if(!favoriteIds.isArray() || favoriteIds.size()>3)throw invalid();
      for(var id:favoriteIds){if(!id.isIntegralNumber() || !selected.contains(id.asLong()) || favorites.stream().anyMatch(g->g.appId()==id.asLong())){log.warn("Worldcup final response rejected: favorite outside positive choices or duplicate");throw invalid();}favorites.add(catalog.game(id.asLong()));}
      if(!complete && !favorites.isEmpty())throw invalid();
      return new Result(explanation,List.copyOf(pair),complete,summary,traits,evidence,uncertainty,List.copyOf(favorites));
    }finally{limit.end(userId);}
  }
  private static String string(com.fasterxml.jackson.databind.JsonNode n,String key,int max,boolean empty){var v=n.path(key);if(!v.isTextual()||v.asText().length()>max||(!empty&&v.asText().isBlank())){log.warn("Worldcup field rejected: {}, type={}, length={}",key,v.getNodeType(),v.asText().length());throw invalid();}return v.asText();}
  private static List<String> list(com.fasterxml.jackson.databind.JsonNode n,String key,int max){var v=n.path(key);if(!v.isArray()||v.size()>max){log.warn("Worldcup list rejected: {}, type={}, count={}",key,v.getNodeType(),v.size());throw invalid();}var result=new ArrayList<String>();for(var item:v){if(!item.isTextual()||item.asText().isBlank()||item.asText().length()>350){log.warn("Worldcup list item rejected: {}, length={}",key,item.asText().length());throw invalid();}result.add(item.asText());}return List.copyOf(result);}
  private static AiException invalid(){return new AiException(BAD_GATEWAY,"AI 대결 정보를 확인하지 못했습니다. 다시 시도해주세요.");}
}
