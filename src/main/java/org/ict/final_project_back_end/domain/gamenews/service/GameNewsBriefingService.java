package org.ict.final_project_back_end.domain.gamenews.service;

import org.ict.final_project_back_end.domain.gamenews.exception.BriefingGenerationException;

import org.ict.final_project_back_end.domain.game.repository.GameRepository;
import org.ict.final_project_back_end.domain.gamenews.client.*;
import org.ict.final_project_back_end.domain.gamenews.dto.NewsBriefingResult;
import org.ict.final_project_back_end.domain.gamenews.repository.GameNewsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@Transactional(propagation = Propagation.NEVER)
public class GameNewsBriefingService {
  private final GameRepository games;
  private final GameNewsRepository news;
  private final NewsBriefingAiClient ai;
  private final GameNewsBriefingWriter writer;
  private final Clock clock;

  @Autowired
  public GameNewsBriefingService(GameRepository games, GameNewsRepository news, NewsBriefingAiClient ai, GameNewsBriefingWriter writer) {
    this(games, news, ai, writer, Clock.systemUTC());
  }

  GameNewsBriefingService(GameRepository games, GameNewsRepository news, NewsBriefingAiClient ai, GameNewsBriefingWriter writer, Clock clock) {
    this.games = games; this.news = news; this.ai = ai; this.writer = writer; this.clock = clock;
  }

  public List<Long> latestNewsIds(long appId, int count) {
    SteamNewsClient.validateRequest(appId, count);
    if (!games.existsById(appId)) throw new IllegalArgumentException("Game not found in games: " + appId);
    return news.findLatestIds(appId, PageRequest.of(0, count));
  }

  public NewsBriefingResult generate(long newsId) {
    var claim = writer.claim(newsId, ai.modelName(), ai.promptVersion(), now());
    return generateClaimed(newsId, claim);
  }

  /** Automation does not regenerate unchanged content merely because the model/prompt changed. */
  public NewsBriefingResult generateIfSourceChanged(long newsId) {
    return generateClaimed(newsId, writer.claimIfSourceChanged(newsId, ai.modelName(), ai.promptVersion(), now()));
  }

  private NewsBriefingResult generateClaimed(long newsId, GameNewsBriefingWriter.Claim claim) {
    if (claim.work() == null) return new NewsBriefingResult(newsId, claim.skipped());
    var work = claim.work();
    try {
      var draft = ai.generate(work.source());
      return new NewsBriefingResult(newsId, writer.complete(work, draft, now()));
    } catch (RuntimeException failure) {
      String code = failure instanceof BriefingGenerationException ? failure.getMessage() : "AI_GENERATION_FAILED";
      try {
        return new NewsBriefingResult(newsId, writer.fail(work, now(), code));
      } catch (RuntimeException persistenceFailure) {
        failure.addSuppressed(persistenceFailure);
        throw failure;
      }
    }
  }

  private LocalDateTime now() {
    return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
  }
}
