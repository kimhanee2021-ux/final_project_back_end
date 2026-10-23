package org.ict.final_project_back_end.domain.rank.dto;

import org.ict.final_project_back_end.domain.rank.entity.WeeklyRankChart;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record WeeklyRankResponse(Long chartId, String countryCode, LocalDate weekStart,
    LocalDate weekEndExclusive, String sourceType, String verificationStatus, String sourceUrl,
    LocalDateTime fetchedAt, List<RankGameResponse> games) {

  public static WeeklyRankResponse from(WeeklyRankChart chart, List<RankGameResponse> games) {
    return new WeeklyRankResponse(chart.getChartId(), chart.getCountryCode(), chart.getWeekStart(),
        chart.getWeekEndExclusive(), chart.getSourceType(), chart.getVerificationStatus(),
        chart.getSourceUrl(), chart.getFetchedAt(), List.copyOf(games));
  }
}
