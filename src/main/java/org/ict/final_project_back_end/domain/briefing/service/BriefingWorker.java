package org.ict.final_project_back_end.domain.briefing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.ict.final_project_back_end.domain.briefing.entity.*;
import org.ict.final_project_back_end.domain.briefing.repository.*;

@Service
public class BriefingWorker {
  private final BriefingCatalogRepository catalog;
  private final OfficialNewsRepository news;
  private final GameBriefingRepository briefings;
  private final BriefingCheckpointRepository checkpoints;
  private final SteamOfficialNewsClient steam;
  private final BriefingAiClient ai;
  private final ObjectMapper json=new ObjectMapper();
  private final boolean enabled,aiEnabled;
  private final int batch,dailyLimit;
  private final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(getClass());
  public BriefingWorker(BriefingCatalogRepository catalog,OfficialNewsRepository news,GameBriefingRepository briefings,
      BriefingCheckpointRepository checkpoints,SteamOfficialNewsClient steam,BriefingAiClient ai,
      @Value("${briefing.enabled:false}") boolean enabled,@Value("${briefing.ai-enabled:false}") boolean aiEnabled,
      @Value("${briefing.games-per-tick:5}") int batch,@Value("${briefing.daily-ai-limit:20}") int dailyLimit) {
    this.catalog=catalog;this.news=news;this.briefings=briefings;this.checkpoints=checkpoints;this.steam=steam;this.ai=ai;
    this.enabled=enabled;this.aiEnabled=aiEnabled;this.batch=Math.clamp(batch,1,10);this.dailyLimit=Math.clamp(dailyLimit,0,100);
  }
  // 승인된 두 계정 자료에 포함된 게임만 순환. 전체 games 테이블을 조회하지 않는다.
  @Scheduled(fixedDelayString="${briefing.interval-ms:60000}",initialDelayString="${briefing.initial-delay-ms:30000}")
  public synchronized void tick() {
    if(!enabled)return;
    var checkpoint=checkpoints.findById(1L).orElseGet(()->new BriefingCheckpoint(1));
    checkpoint.attempted(Instant.now());checkpoints.save(checkpoint);
    var games=catalog.next(checkpoint.getCursorAppId(),PageRequest.of(0,batch));
    if(games.isEmpty()) {checkpoint.rewind();checkpoints.save(checkpoint);}
    for(var game:games) {
      try {
        for(var source:steam.fetch(game.getAppId())) {
          var row=news.findByAppIdAndGid(game.getAppId(),source.gid()).orElseGet(()->new OfficialNews(game.getAppId(),game.getGameName(),source.gid()));
          row.refresh(source.title(),source.content(),source.url(),source.publishedAt(),Instant.now(),source.fingerprint());news.save(row);
        }
        checkpoint.advance(game.getAppId(),Instant.now());checkpoints.save(checkpoint);
      } catch(Exception e) {
        checkpoint.failed("STEAM_FETCH_FAILED");checkpoints.save(checkpoint);
        log.warn("Briefing source fetch failed appId={} type={}",game.getAppId(),e.getClass().getSimpleName());
        // 실패한 게임에서 영원히 정지하지 않고 다음 순환에서 다시 검사한다.
        checkpoint.skip(game.getAppId());checkpoints.save(checkpoint);
        break;
      }
    }
    if(aiEnabled && ai.configured()) analyzePending();
  }
  private void analyzePending() {
    var quota=checkpoints.findById(2L).orElseGet(()->new BriefingCheckpoint(2));
    if("AI_QUOTA_EXHAUSTED".equals(quota.getStatus()) && LocalDate.now(ZoneOffset.UTC).equals(quota.getQuotaDay()))return;
    if("AI_ANALYSIS_FAILED".equals(quota.getStatus()) && quota.getLastAttemptAt()!=null && quota.getLastAttemptAt().isAfter(Instant.now().minusSeconds(900)))return;
    for(var appId:news.pending(PageRequest.of(0,5))) {
      var sources=news.findTop5ByAppIdOrderByPublishedAtDesc(appId);
      if(sources.isEmpty())continue;
      String hash=hash(sources);
      var existing=briefings.findById(appId);
      if(existing.isPresent() && hash.equals(existing.get().getSourceHash())) {
        existing.get().checked(Instant.now());briefings.save(existing.get());continue;
      }
      if(!quota.reserve(LocalDate.now(ZoneOffset.UTC),dailyLimit)){quota.failed("DAILY_AI_LIMIT");checkpoints.save(quota);return;}
      quota.attempted(Instant.now());checkpoints.save(quota);
      try {
        var result=ai.analyze(sources);
        if(!hash.equals(hash(news.findTop5ByAppIdOrderByPublishedAtDesc(appId))))throw new IllegalStateException("SOURCE_CHANGED");
        for(var translated:result.translations()) {
          var row=news.findByAppIdAndGid(appId,translated.gid()).orElseThrow();
          row.translate(translated.title(),translated.summary());news.save(row);
        }
        briefings.save(new GameBriefing(appId,sources.getFirst().getGameName(),hash,json.writeValueAsString(Map.of("items",result.items())),Instant.now(),sources.size()));
        quota.advance(appId,Instant.now());checkpoints.save(quota);
      } catch(Exception e) {
        quota.failed("AI_HTTP_429".equals(e.getMessage())?"AI_QUOTA_EXHAUSTED":"AI_ANALYSIS_FAILED");checkpoints.save(quota);
        log.warn("Briefing AI failed appId={} type={}",appId,e.getClass().getSimpleName());
      }
      return; // 1tick 최대1회 AI, 재시도 폭주 방지
    }
  }
  public static String hash(List<OfficialNews> sources) {
    try {
      StringBuilder input=new StringBuilder();
      for(var s:sources.stream().sorted(Comparator.comparing(OfficialNews::getGid)).toList()) input.append(s.getGid()).append('\u0000').append(s.getTitle()).append('\u0000').append(s.getContent()).append('\u0000').append(s.getPublishedAt().getEpochSecond()).append('\n');
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.toString().getBytes(StandardCharsets.UTF_8)));
    } catch(Exception e){throw new IllegalStateException(e);}
  }
  public Map<String,Object> status() {
    var scan=checkpoints.findById(1L).orElseGet(()->new BriefingCheckpoint(1));
    var aiStatus=checkpoints.findById(2L).orElseGet(()->new BriefingCheckpoint(2));
    Map<String,Object> result=new LinkedHashMap<>();
    result.put("catalogGames",catalog.count());result.put("storedNews",news.count());result.put("analyzedGames",briefings.count());result.put("translatedPosts",news.countByKoreanSummaryIsNotNull());
    result.put("enabled",enabled);result.put("aiEnabled",aiEnabled);result.put("aiConfigured",ai.configured());
    result.put("scan",scan);result.put("analysis",aiStatus);result.put("dailyAiLimit",dailyLimit);return result;
  }
}
