package org.ict.final_project_back_end.domain.gamenews.service;

import java.time.Instant;
import java.util.*;
import org.ict.final_project_back_end.domain.game.service.BbsGameSearchService;
import org.ict.final_project_back_end.domain.gamenews.client.BbsBriefingClient;
import org.ict.final_project_back_end.domain.gamenews.dto.*;
import org.ict.final_project_back_end.domain.gamenews.dto.BbsNewsResponse.Article;
import org.ict.final_project_back_end.domain.gamenews.exception.*;
import org.ict.final_project_back_end.domain.gamenews.repository.BbsBriefingRepository;
import org.ict.final_project_back_end.domain.gamenews.repository.BbsBriefingRepository.Saved;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class BbsBriefingService {
  private final boolean enabled;
  private final BbsOfficialNewsService news;
  private final BbsGameSearchService games;
  private final BbsBriefingRepository repository;
  private final BbsBriefingClient client;
  private final BbsVoiceRequestGuard limits;
  public BbsBriefingService(BbsOfficialNewsService news, BbsGameSearchService games,
      BbsBriefingRepository repository, BbsBriefingClient client,
      @Value("${app.bbs.briefing.enabled:${BBS_BRIEFING_ENABLED:false}}") boolean enabled,
      @Value("${app.bbs.briefing.daily-calls:100}") int daily,
      @Value("${app.bbs.briefing.calls-per-minute:3}") int perMinute,
      @Value("${app.bbs.briefing.concurrent-calls:2}") int concurrent) {
    this.news=news;this.games=games;this.repository=repository;this.client=client;this.enabled=enabled;
    // Reuse the existing bounded guard algorithm with a separate budget from STT.
    this.limits=new BbsVoiceRequestGuard(daily,perMinute,concurrent);
  }
  private void validate(long appId, String gid, String hash) {
    if (appId < 1 || appId > 0xffffffffL || gid == null || !gid.matches("[0-9]{1,64}")
        || hash == null || !hash.matches("[a-f0-9]{64}")) throw BbsBriefingException.invalid();
  }
  private Article source(long appId, String gid, String hash, boolean generating) {
    var snapshot = news.get(appId);
    if (generating && snapshot.stale()) throw new BbsBriefingException(HttpStatus.SERVICE_UNAVAILABLE,
        "BBS_BRIEFING_SOURCE_STALE", "최신 공식 원문을 확인하지 못했어요. 소식을 새로 확인한 뒤 요약해 주세요.");
    var article = snapshot.items().stream().filter(a -> a.id().equals(gid)).findFirst()
        .orElseThrow(() -> new BbsBriefingException(HttpStatus.NOT_FOUND, "BBS_BRIEFING_NEWS_NOT_FOUND", "선택한 공식 소식을 찾을 수 없어요. 목록을 다시 확인해 주세요."));
    if (!BriefingContent.hash(article).equals(hash)) throw BbsBriefingException.changed();
    return article;
  }
  public BbsBriefingResponse get(long appId, String gid, String hash) {
    validate(appId,gid,hash);
    if (!enabled) return BbsBriefingResponse.state("UNAVAILABLE");
    var article = source(appId,gid,hash,false);
    try { return response(repository.find(appId,gid),article); }
    catch (DataAccessException ex) { throw BbsBriefingException.storage(); }
  }
  private BbsBriefingResponse response(Optional<Saved> saved, Article article) {
    String hash=BriefingContent.hash(article);
    if (saved.isPresent() && saved.get().matches(hash,client.model())) {
      var s=saved.get(); var draft=BriefingContent.decode(s.payload());
      return new BbsBriefingResponse("READY",new BbsBriefingResponse.Briefing(draft.titleKo(),draft.points(),draft.narrationScript(),
          s.hash(),s.model(),s.version(),s.generatedAt(),BriefingContent.seconds(draft.narrationScript()),article.sourceUrl(),article.publishedAt()));
    }
    if (saved.isPresent() && saved.get().claimUntil()!=null && saved.get().claimUntil().isAfter(Instant.now()))
      return BbsBriefingResponse.state("PROCESSING");
    if (!client.configured()) return BbsBriefingResponse.state("UNAVAILABLE");
    return BbsBriefingResponse.state(saved.isPresent() && saved.get().payload()!=null ? "STALE" : "EMPTY");
  }
  public BbsBriefingResponse generate(long appId, String gid, String hash, String address) {
    validate(appId,gid,hash);
    if (!enabled) throw BbsBriefingException.notReady();
    Article article=source(appId,gid,hash,true);
    try {
      var existing=response(repository.find(appId,gid),article);
      if (existing.status().equals("READY")) return existing;
      if (existing.status().equals("PROCESSING")) throw BbsBriefingException.busy();
      if (!client.configured()) throw BbsBriefingException.notReady();
      BriefingContent.validateSource(article);
      String gameName=games.find(appId).name();
      try (var permit=acquire(address)) {
        long id=repository.ensure(appId,article);
        String token=UUID.randomUUID().toString();
        if (!repository.claim(id,token)) throw BbsBriefingException.busy();
        try {
          // Another instance may have completed between the first read and acquiring this lease.
          existing=response(repository.find(appId,gid),article);
          if (existing.status().equals("READY")) return existing;
          var draft=BriefingContent.validate(client.generate(gameName,article));
          String payload=BriefingContent.encode(draft,article);
          if (!repository.save(id,token,payload,hash,client.model())) throw BbsBriefingException.busy();
          return response(repository.find(appId,gid),article);
        } finally {
          try { repository.release(id,token); }
          catch (DataAccessException ex) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("BBS briefing lease release failed; bounded lease will expire."); }
        }
      }
    } catch (DataAccessException ex) { throw BbsBriefingException.storage(); }
  }
  private BbsVoiceRequestGuard.Permit acquire(String address) {
    try { return limits.acquire(address); }
    catch (BbsVoiceException ex) { throw BbsBriefingException.limited(); }
  }
}
