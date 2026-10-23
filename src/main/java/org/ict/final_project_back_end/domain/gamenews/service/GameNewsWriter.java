package org.ict.final_project_back_end.domain.gamenews.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.ict.final_project_back_end.domain.game.entity.Game;
import org.ict.final_project_back_end.domain.gamenews.dto.*;
import org.ict.final_project_back_end.domain.gamenews.entity.*;
import org.ict.final_project_back_end.domain.gamenews.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Short database transactions only; HTTP calls must finish before entering this writer. */
@Service
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class GameNewsWriter {
  private final EntityManager em;
  private final GameNewsRepository news;
  private final GameNewsSyncStateRepository states;

  public GameNewsWriter(EntityManager em, GameNewsRepository news, GameNewsSyncStateRepository states) {
    this.em = em;
    this.news = news;
    this.states = states;
  }

  public GameNewsCollectionResult save(long appId, List<SteamNewsItem> items,
                                       LocalDateTime attemptedAt, LocalDateTime completedAt) {
    Game game = lockGame(appId);
    GameNewsSyncState state = stateFor(game);
    if (state.isNewerThan(attemptedAt)) {
      return new GameNewsCollectionResult(appId, items.size(), 0, 0, 0, true);
    }
    int inserted = 0, updated = 0, unchanged = 0;
    for (SteamNewsItem item : items) {
      if (item.appId() != appId) throw new IllegalArgumentException("News belongs to a different game");
      String hash = item.contentHash();
      GameNews existing = news.findForUpdate(appId, item.sourceNewsId()).orElse(null);
      if (existing == null) {
        news.save(GameNews.builder().game(game).sourceNewsId(item.sourceNewsId())
            .feedName(SteamNewsItem.OFFICIAL_FEED).title(item.title()).content(item.content())
            .sourceUrl(item.sourceUrl()).publishedAt(item.publishedAt()).contentHash(hash)
            .collectedAt(completedAt).updatedAt(completedAt).lastSeenAt(completedAt).build());
        inserted++;
      } else if (existing.refreshSource(item.title(), item.content(), item.sourceUrl(),
          item.publishedAt(), hash, completedAt)) {
        GameNewsBriefing briefing = em.find(GameNewsBriefing.class, existing.getId());
        if (briefing != null) briefing.markPending();
        updated++;
      } else {
        unchanged++;
      }
    }
    state.recordSuccess(attemptedAt, completedAt);
    states.save(state);
    return new GameNewsCollectionResult(appId, items.size(), inserted, updated, unchanged, false);
  }

  public void recordFailure(long appId, LocalDateTime attemptedAt, LocalDateTime completedAt, String message) {
    GameNewsSyncState state = stateFor(lockGame(appId));
    if (state.isNewerThan(attemptedAt)) return;
    state.recordFailure(attemptedAt, completedAt, message);
    states.save(state);
  }

  public LocalDateTime claimDueCollection(long appId, LocalDateTime now) {
    GameNewsSyncState state = stateFor(lockGame(appId));
    LocalDateTime token = state.claimIfDue(now);
    if (token != null) states.save(state);
    return token;
  }

  private Game lockGame(long appId) {
    // The parent already exists, so this also serializes first-time state/news inserts across JVMs.
    Game game = em.find(Game.class, appId, LockModeType.PESSIMISTIC_WRITE);
    if (game == null) throw new IllegalArgumentException("Game not found in games: " + appId);
    return game;
  }

  private GameNewsSyncState stateFor(Game game) {
    return states.findById(game.getAppId()).orElseGet(() -> GameNewsSyncState.builder().game(game).build());
  }
}
