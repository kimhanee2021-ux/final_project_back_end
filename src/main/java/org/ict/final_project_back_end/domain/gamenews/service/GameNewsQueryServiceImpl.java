package org.ict.final_project_back_end.domain.gamenews.service;

import org.ict.final_project_back_end.domain.game.exception.GameNotFoundException;
import org.ict.final_project_back_end.domain.game.repository.GameRepository;
import org.ict.final_project_back_end.domain.gamenews.dto.*;
import org.ict.final_project_back_end.domain.gamenews.entity.GameNewsCategory;
import org.ict.final_project_back_end.domain.gamenews.exception.*;
import org.ict.final_project_back_end.domain.gamenews.repository.GameNewsBriefingRepository;
import org.ict.final_project_back_end.domain.gamenews.repository.GameNewsBriefingRepository.SummaryRow;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class GameNewsQueryServiceImpl implements GameNewsQueryService {
  private final GameRepository games;
  private final GameNewsBriefingRepository briefings;
  public GameNewsQueryServiceImpl(GameRepository games, GameNewsBriefingRepository briefings) {
    this.games = games; this.briefings = briefings;
  }

  @Override public GameNewsPageResponse list(Long appId, String category, int page, int size) {
    if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
      throw new InvalidGameNewsQueryException("page는 0 이상, size는 1~100이며 조회 위치가 너무 크지 않아야 합니다");
    }
    GameNewsCategory filter = category(category);
    if (appId != null) requireGame(appId);
    return GameNewsPageResponse.from(briefings.findPublishedSummaries(appId, filter, PageRequest.of(page, size))
        .map(this::summary));
  }

  @Override public GameNewsDetailResponse detail(long appId, long newsId) {
    if (newsId <= 0) throw new InvalidGameNewsQueryException("소식 번호는 양수여야 합니다");
    requireGame(appId);
    var row = briefings.findPublishedDetail(appId, newsId).orElseThrow(GameNewsNotFoundException::new);
    var item = summary(row);
    return new GameNewsDetailResponse(item.newsId(), item.appId(), item.gameName(), item.title(), item.category(),
        item.summary(), row.getAiContent(), item.sourceUrl(), item.publishedAt(), item.sourceUpdatedAt(),
        item.generatedAt(), item.generationStatus(), item.stale());
  }

  private GameNewsSummaryResponse summary(SummaryRow row) {
    return new GameNewsSummaryResponse(row.getNewsId(), row.getAppId(), row.getGameName(), row.getTitle(),
        row.getCategory(), row.getSummary(), row.getSourceUrl(), utc(row.getPublishedAt()),
        utc(row.getSourceUpdatedAt()), utc(row.getGeneratedAt()), row.getGenerationStatus(),
        !Objects.equals(row.getSourceHash(), row.getGeneratedFromHash()));
  }

  private void requireGame(long appId) {
    if (appId <= 0 || appId > 0xffffffffL) throw new InvalidGameNewsQueryException("유효한 Steam 게임 번호가 필요합니다");
    if (!games.existsById(appId)) throw new GameNotFoundException();
  }

  private GameNewsCategory category(String input) {
    if (input == null || input.isBlank()) return null;
    if (input.length() <= 30) {
      try { return GameNewsCategory.valueOf(input.strip().toUpperCase(Locale.ROOT)); }
      catch (IllegalArgumentException ignored) { }
    }
    throw new InvalidGameNewsQueryException("category는 NOTICE, PATCH, EVENT, MAINTENANCE, DEV_NEWS, OTHER 중 하나여야 합니다");
  }

  private Instant utc(LocalDateTime value) { return value == null ? null : value.toInstant(ZoneOffset.UTC); }
}
