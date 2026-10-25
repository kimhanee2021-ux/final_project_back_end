package org.ict.final_project_back_end.domain.briefing.controller;

import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.ict.final_project_back_end.domain.briefing.repository.*;
import org.ict.final_project_back_end.domain.briefing.service.*;

@RestController @RequestMapping("/api/board")
public class BriefingController {
  private final OfficialNewsRepository news;
  private final GameBriefingRepository briefings;
  private final BriefingWorker worker;
  public BriefingController(OfficialNewsRepository news,GameBriefingRepository briefings,BriefingWorker worker){this.news=news;this.briefings=briefings;this.worker=worker;}
  private void validate(int page,Long appId){if(page<0 || page>10000 || (appId!=null && appId<=0))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"올바른 페이지와 게임 ID를 입력하세요.");}
  @GetMapping("/briefing")
  public Map<String,Object> briefing(@RequestParam(defaultValue="0") int page,@RequestParam(required=false) Long appId,
      @RequestParam(required=false) String group,@RequestParam(required=false) String topic) {
    validate(page,appId);
    if(group!=null && !Set.of("NOTICE","UPDATE","EVENT").contains(group))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"잘못된 분류입니다.");
    if(topic!=null && !BriefingContent.TOPICS.containsKey(topic))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"잘못된 소주제입니다.");
    var results=briefings.list(appId,PageRequest.of(0,300));
    List<Map<String,Object>> cards=new ArrayList<>();
    for(var b:results) {
      var sources=news.findTop5ByAppIdOrderByPublishedAtDesc(b.getAppId());
      boolean stale=!BriefingWorker.hash(sources).equals(b.getSourceHash());
      var parsed=stale?List.<BriefingContent.Item>of():BriefingContent.parse(b.getResultJson(),sources.stream().map(s->s.getGid()).collect(java.util.stream.Collectors.toSet()));
      var items=parsed.stream().filter(item->group==null || (item.topic()!=null && group.equals(BriefingContent.TOPICS.get(item.topic()))))
          .filter(item->topic==null || topic.equals(item.topic())).toList();
      if(items.isEmpty() && (group!=null || topic!=null))continue;
      var sourceIds=items.stream().flatMap(item->item.sourceIds().stream()).collect(java.util.stream.Collectors.toSet());
      var latestSourceAt=sources.stream().filter(source->sourceIds.contains(source.getGid())).map(source->source.getPublishedAt()).max(java.time.Instant::compareTo).orElse(java.time.Instant.EPOCH);
      cards.add(Map.of("latestSourceAt",latestSourceAt,"appId",b.getAppId(),"gameName",b.getGameName(),"analyzedAt",b.getAnalyzedAt(),"sourceCount",b.getSourceCount(),"stale",stale,"items",items,"sources",sources));
    }
    cards.sort(Comparator.comparing((Map<String,Object> card)->(java.time.Instant)card.get("latestSourceAt")).reversed());
    int start=Math.min(cards.size(),page*10),end=Math.min(cards.size(),start+10);
    return Map.of("items",cards.subList(start,end),"total",cards.size(),"last",end==cards.size(),"status",worker.status());
  }
}
