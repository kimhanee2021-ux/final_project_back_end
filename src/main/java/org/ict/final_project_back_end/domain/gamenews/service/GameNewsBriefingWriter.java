package org.ict.final_project_back_end.domain.gamenews.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.ict.final_project_back_end.domain.gamenews.client.NewsBriefingAiClient.*;
import org.ict.final_project_back_end.domain.gamenews.dto.NewsBriefingResult.Outcome;
import org.ict.final_project_back_end.domain.gamenews.entity.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

/** Article row is the shared lock for source updates, claim creation and completion. No AI call here. */
@Service
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class GameNewsBriefingWriter {
  public static final int LEASE_MINUTES = 10;
  private final EntityManager em;
  public GameNewsBriefingWriter(EntityManager em) { this.em = em; }

  public record Work(Source source, LocalDateTime startedAt, String model, String prompt) { }
  public record Claim(Work work, Outcome skipped) { }

  public Claim claim(long newsId, String model, String prompt, LocalDateTime now) {
    return claim(newsId, model, prompt, now, false);
  }

  public Claim claimIfSourceChanged(long newsId, String model, String prompt, LocalDateTime now) {
    return claim(newsId, model, prompt, now, true);
  }

  private Claim claim(long newsId, String model, String prompt, LocalDateTime now, boolean sourceChangesOnly) {
    if (model == null || model.isBlank() || model.length() > 100 || prompt == null || prompt.isBlank() || prompt.length() > 30) {
      throw new IllegalArgumentException("Invalid briefing model/prompt configuration");
    }
    GameNews source = lockNews(newsId);
    GameNewsBriefing briefing = em.find(GameNewsBriefing.class, newsId);
    if (briefing == null) {
      briefing = GameNewsBriefing.builder().news(source).build();
      em.persist(briefing);
    }
    if (briefing.getGenerationStatus() == BriefingGenerationStatus.PROCESSING
        && briefing.getProcessingStartedAt() != null
        && briefing.getProcessingStartedAt().isAfter(now.minusMinutes(LEASE_MINUTES))) {
      return new Claim(null, Outcome.BUSY);
    }
    if (Objects.equals(briefing.getGeneratedFromHash(), source.getContentHash())
        && briefing.getGeneratedAt() != null && briefing.getAiContent() != null
        && briefing.getSummary() != null && briefing.getCategory() != null
        && (sourceChangesOnly || (briefing.getGenerationStatus() == BriefingGenerationStatus.SUCCEEDED
            && Objects.equals(briefing.getModelName(), model) && Objects.equals(briefing.getPromptVersion(), prompt)))) {
      return new Claim(null, Outcome.UP_TO_DATE);
    }
    if (briefing.getGenerationStatus() == BriefingGenerationStatus.FAILED
        && (briefing.getNextRetryAt() == null || briefing.getNextRetryAt().isAfter(now))) {
      return new Claim(null, Outcome.DEFERRED);
    }
    // Use microsecond precision (MySQL DATETIME(6)); late workers must match this attempt exactly.
    LocalDateTime startedAt = now;
    if (briefing.getProcessingStartedAt() != null && !startedAt.isAfter(briefing.getProcessingStartedAt())) {
      startedAt = briefing.getProcessingStartedAt().plusNanos(1000);
    }
    briefing.startProcessing(startedAt);
    var snapshot = new Source(newsId, source.getAppId(), source.getGame().getName(), source.getTitle(),
        source.getContent(), source.getSourceUrl(), source.getPublishedAt(), source.getContentHash());
    return new Claim(new Work(snapshot, startedAt, model, prompt), null);
  }

  public Outcome complete(Work work, Draft draft, LocalDateTime now) {
    GameNews source = lockNews(work.source().newsId());
    GameNewsBriefing briefing = em.find(GameNewsBriefing.class, source.getId());
    if (briefing == null || !briefing.ownsAttempt(work.startedAt())) return Outcome.STALE;
    if (!source.getContentHash().equals(work.source().contentHash())) {
      briefing.markPending();
      return Outcome.STALE;
    }
    briefing.complete(draft.category(), draft.summary(), draft.aiContent(), work.source().contentHash(),
        work.model(), work.prompt(), now);
    return Outcome.GENERATED;
  }

  public Outcome fail(Work work, LocalDateTime now, String errorCode) {
    GameNews source = lockNews(work.source().newsId());
    GameNewsBriefing briefing = em.find(GameNewsBriefing.class, source.getId());
    if (briefing == null || !briefing.ownsAttempt(work.startedAt())) return Outcome.STALE;
    if (!source.getContentHash().equals(work.source().contentHash())) {
      briefing.markPending();
      return Outcome.STALE;
    }
    briefing.fail(now, errorCode);
    return Outcome.FAILED;
  }

  private GameNews lockNews(long id) {
    GameNews source = em.find(GameNews.class, id, LockModeType.PESSIMISTIC_WRITE);
    if (source == null) throw new IllegalArgumentException("News not found: " + id);
    return source;
  }
}
