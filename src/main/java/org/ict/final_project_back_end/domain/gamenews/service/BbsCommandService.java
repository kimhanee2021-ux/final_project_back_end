package org.ict.final_project_back_end.domain.gamenews.service;

import java.util.List;
import org.ict.final_project_back_end.domain.game.dto.BbsGameResponse;
import org.ict.final_project_back_end.domain.game.service.BbsGameSearchService;
import org.ict.final_project_back_end.domain.gamenews.client.*;
import org.ict.final_project_back_end.domain.gamenews.client.BbsCommandClient.Action;
import org.ict.final_project_back_end.domain.gamenews.exception.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class BbsCommandService {
  private final BbsCommandClient client;
  private final BbsGameSearchService games;
  private final BbsVoiceRequestGuard limits;
  public BbsCommandService(BbsCommandClient client,BbsGameSearchService games,
      @Value("${app.bbs.command.daily-calls:100}") int daily,
      @Value("${app.bbs.command.calls-per-minute:6}") int perMinute,
      @Value("${app.bbs.command.concurrent-calls:2}") int concurrent) {
    this.client=client;this.games=games;this.limits=new BbsVoiceRequestGuard(daily,perMinute,concurrent);
  }
  public record Result(String status,Action action,String query,BbsGameResponse game,List<BbsGameResponse> candidates,boolean hasMore) {}
  public Result resolve(String transcript,String address) {
    if(transcript==null || transcript.isBlank() || transcript.length()>255 || transcript.codePoints().anyMatch(Character::isISOControl)) throw BbsCommandException.invalid();
    if(!client.configured()) throw BbsCommandException.notReady();
    BbsCommandClient.Intent intent;
    try(var permit=acquire(address)) { intent=OpenAiBbsCommandClient.validate(client.interpret(transcript.strip())); }
    var action=intent.action();String query=intent.gameQuery();
    if(action==Action.UNSUPPORTED) return new Result("UNSUPPORTED",action,query,null,List.of(),false);
    if(java.util.Set.of(Action.NEXT_BRIEFING,Action.PAUSE,Action.RESUME).contains(action))
      return new Result("CONTROL",action,"",null,List.of(),false);
    if(query.isEmpty()) return new Result("NEEDS_GAME",action,query,null,List.of(),false);
    if(action==Action.SEARCH) return new Result("SEARCH",action,query,null,List.of(),false);
    var exact=games.exact(query);
    var matches=games.search(query,12);
    // A bare series title can be an older game's exact name after STT drops a sequel number.
    // Ask for selection when several games match, unless the exact title contains an explicit number.
    if(exact.size()==1 && (query.codePoints().anyMatch(Character::isDigit) || matches.content().size()==1 && !matches.hasMore()))
      return new Result("RESOLVED",action,query,exact.getFirst(),List.of(),false);
    if(exact.isEmpty() && matches.content().size()==1 && !matches.hasMore())
      return new Result("RESOLVED",action,query,matches.content().getFirst(),List.of(),false);
    return new Result(matches.content().isEmpty()?"NOT_FOUND":"CHOOSE_GAME",action,query,null,matches.content(),matches.hasMore());
  }
  private BbsVoiceRequestGuard.Permit acquire(String address) {
    try { return limits.acquire(address); } catch(BbsVoiceException ex) { throw BbsCommandException.limited(); }
  }
}
