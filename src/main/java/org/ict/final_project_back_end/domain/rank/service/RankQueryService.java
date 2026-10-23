package org.ict.final_project_back_end.domain.rank.service;

import org.ict.final_project_back_end.domain.game.entity.Game;
import org.ict.final_project_back_end.domain.game.repository.GameRepository;
import org.ict.final_project_back_end.domain.rank.dto.RankGameResponse;
import org.ict.final_project_back_end.domain.rank.dto.WeeklyRankResponse;
import org.ict.final_project_back_end.domain.rank.entity.WeeklyRankChart;
import org.ict.final_project_back_end.domain.rank.entity.WeeklyRankEntry;
import org.ict.final_project_back_end.domain.rank.exception.WeeklyRankNotFoundException;
import org.ict.final_project_back_end.domain.rank.repository.WeeklyRankChartRepository;
import org.ict.final_project_back_end.domain.rank.repository.WeeklyRankEntryRepository;
import org.ict.final_project_back_end.global.exception.InvalidRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// DB 조회 전용. 누락된 국가/주를 다른 차트나 실시간 Steam 데이터로 대체하지 않는다.
@Service
@Transactional(readOnly = true)
public class RankQueryService {

  private final WeeklyRankChartRepository chartRepository;
  private final WeeklyRankEntryRepository entryRepository;
  private final GameRepository gameRepository;

  public RankQueryService(WeeklyRankChartRepository chartRepository,
      WeeklyRankEntryRepository entryRepository, GameRepository gameRepository) {
    this.chartRepository = chartRepository;
    this.entryRepository = entryRepository;
    this.gameRepository = gameRepository;
  }

  public WeeklyRankResponse getWeeklyRank(String country, LocalDate week) {
    if (country == null || !country.trim().matches("[a-zA-Z]{2}")) {
      throw new InvalidRequestException("country는 영문 두 자리 국가 코드여야 합니다.");
    }
    if (week == null) {
      throw new InvalidRequestException("week는 조회할 주의 시작일이어야 합니다.");
    }
    String countryCode = country.trim().toUpperCase(Locale.ROOT);
    WeeklyRankChart chart = chartRepository.findByCountryCodeAndWeekStart(countryCode, week)
        .orElseThrow(WeeklyRankNotFoundException::new);
    List<WeeklyRankEntry> entries = entryRepository.findTop10ByChart_ChartIdOrderByGameRankAsc(chart.getChartId());
    if (entries.isEmpty()) {
      return WeeklyRankResponse.from(chart, List.of());
    }

    // 최대 10개의 메타데이터를 한 번에 조회하며, 누락된 games 행 때문에 순위를 버리지 않는다.
    Map<Long, Game> games = gameRepository.findAllById(
            entries.stream().map(WeeklyRankEntry::getAppId).toList()).stream()
        .collect(Collectors.toMap(Game::getAppId, Function.identity()));
    return WeeklyRankResponse.from(chart, entries.stream()
        .map(entry -> RankGameResponse.from(entry, games.get(entry.getAppId())))
        .toList());
  }
}
