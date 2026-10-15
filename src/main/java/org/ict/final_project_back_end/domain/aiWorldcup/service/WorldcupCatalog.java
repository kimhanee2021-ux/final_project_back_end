package org.ict.final_project_back_end.domain.aiWorldcup.service;
import org.ict.final_project_back_end.domain.aiWorldcup.dto.WorldcupRequest;
import org.ict.final_project_back_end.domain.aiWorldcup.dto.WorldcupGame;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class WorldcupCatalog {
  public record Group(String id,String parent,String name,int count) {}
  private final List<WorldcupGame> games;
  private final List<Group> groups;
  private final Map<Long,WorldcupGame> byId=new HashMap<>();
  public WorldcupCatalog() {
    try {
      var json=new ObjectMapper();
      try(var input=getClass().getResourceAsStream("/ai-worldcup/games.json")){games=List.copyOf(json.readValue(input,new TypeReference<List<WorldcupGame>>(){}));}
      try(var input=getClass().getResourceAsStream("/ai-worldcup/groups.json")){groups=List.copyOf(json.readValue(input,new TypeReference<List<Group>>(){}));}
      for(var game:games)if(byId.put(game.appId(),game)!=null)throw new IllegalStateException("Duplicate catalog ID");
    } catch(Exception e){throw new IllegalStateException("Worldcup catalog could not be loaded",e);}
  }
  public List<WorldcupGame> games(){return games;}
  public List<Group> groups(){return groups;}
  public WorldcupGame game(long id){return byId.get(id);}
  public List<WorldcupGame> filtered(List<String> selected){return games.stream().filter(g->selected.isEmpty() || g.groupIds().stream().anyMatch(selected::contains)).toList();}
  public List<WorldcupGame> pool(List<String> selected,List<WorldcupRequest.Choice> history,String seed){
    Set<Long> seen=new HashSet<>();Map<String,Integer> liked=new HashMap<>();
    for(var choice:history){seen.add(choice.leftId());seen.add(choice.rightId());
      var chosen=new ArrayList<Long>();if(choice.verdict().equals("left")||choice.verdict().equals("both"))chosen.add(choice.leftId());if(choice.verdict().equals("right")||choice.verdict().equals("both"))chosen.add(choice.rightId());
      for(long id:chosen)for(String tag:game(id).tags())liked.merge(tag,1,Integer::sum);
    }
    var available=new ArrayList<>(filtered(selected).stream().filter(g->!seen.contains(g.appId())).toList());
    Collections.shuffle(available,new Random(Objects.hash(seed,history.size())));
    // Reserve half for exploration rather than locking into the first few selections.
    var ranked=new ArrayList<>(available);ranked.sort(Comparator.comparingInt((WorldcupGame g)->g.tags().stream().mapToInt(t->liked.getOrDefault(t,0)).sum()).reversed());
    var result=new LinkedHashMap<Long,WorldcupGame>();ranked.stream().limit(24).forEach(g->result.put(g.appId(),g));
    for(var g:available){if(result.size()>=48)break;result.put(g.appId(),g);}return List.copyOf(result.values());
  }
}
