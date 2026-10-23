package org.ict.final_project_back_end.domain.gamenews.service;

import org.ict.final_project_back_end.domain.gamenews.exception.SteamNewsException;

import org.ict.final_project_back_end.domain.game.repository.GameRepository;
import org.ict.final_project_back_end.domain.gamenews.client.*;
import org.ict.final_project_back_end.domain.gamenews.dto.GameNewsCollectionResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
@Transactional(propagation = Propagation.NEVER)
public class GameNewsCollectionService {
  private final GameRepository games;
  private final SteamNewsClient client;
  private final GameNewsWriter writer;
  private final Clock clock;

  @Autowired
  public GameNewsCollectionService(GameRepository games, SteamNewsClient client, GameNewsWriter writer) {
    this(games, client, writer, Clock.systemUTC());
  }

  GameNewsCollectionService(GameRepository games, SteamNewsClient client, GameNewsWriter writer, Clock clock) {
    this.games = games;
    this.client = client;
    this.writer = writer;
    this.clock = clock;
  }

  /** Explicit collection ignores nextCollectAt, allowing an operator to recheck immediately. */
  public GameNewsCollectionResult collect(long appId, int count) {
    SteamNewsClient.validateRequest(appId, count);
    if (!games.existsById(appId)) throw new IllegalArgumentException("Game not found in games: " + appId);
    LocalDateTime attemptedAt = now();
    return collectClaimed(appId, count, attemptedAt);
  }

  /** Scheduled collection respects success intervals, retry delays and another worker's lease. */
  public Optional<GameNewsCollectionResult> collectIfDue(long appId, int count) {
    SteamNewsClient.validateRequest(appId, count);
    LocalDateTime token = writer.claimDueCollection(appId, now());
    return token == null ? Optional.empty() : Optional.of(collectClaimed(appId, count, token));
  }

  private GameNewsCollectionResult collectClaimed(long appId, int count, LocalDateTime attemptedAt) {
    try {
      var items = client.fetch(appId, count);
      return writer.save(appId, items, attemptedAt, now());
    } catch (RuntimeException failure) {
      String message = failure instanceof SteamNewsException ? failure.getMessage() : "News persistence failed";
      try {
        writer.recordFailure(appId, attemptedAt, now(), message);
      } catch (RuntimeException stateFailure) {
        failure.addSuppressed(stateFailure);
      }
      throw failure;
    }
  }

  private LocalDateTime now() {
    return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
  }
}
