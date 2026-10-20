package org.ict.final_project_back_end.domain.rank.service;

import org.ict.final_project_back_end.domain.game.entity.Game;
import org.ict.final_project_back_end.domain.game.repository.GameRepository;
import org.ict.final_project_back_end.domain.rank.dto.WeeklyRankResponse;
import org.ict.final_project_back_end.domain.rank.entity.WeeklyRankChart;
import org.ict.final_project_back_end.domain.rank.entity.WeeklyRankEntry;
import org.ict.final_project_back_end.domain.rank.exception.WeeklyRankNotFoundException;
import org.ict.final_project_back_end.domain.rank.repository.WeeklyRankChartRepository;
import org.ict.final_project_back_end.domain.rank.repository.WeeklyRankEntryRepository;
import org.ict.final_project_back_end.global.exception.InvalidRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RankQueryServiceTest {

  private static final LocalDate WEEK = LocalDate.of(2026, 10, 6);
  private final WeeklyRankChartRepository charts = mock(WeeklyRankChartRepository.class);
  private final WeeklyRankEntryRepository entries = mock(WeeklyRankEntryRepository.class);
  private final GameRepository games = mock(GameRepository.class);
  private final RankQueryService service = new RankQueryService(charts, entries, games);
  private WeeklyRankChart chart;

  @BeforeEach
  void setup() {
    chart = WeeklyRankChart.builder().chartId(10L).countryCode("KR").weekStart(WEEK)
        .weekEndExclusive(WEEK.plusDays(7)).sourceType("TEST_FIXTURE").verificationStatus("UNVERIFIED")
        .sourceUrl("https://example.invalid/rank-test").fetchedAt(LocalDateTime.of(2026, 10, 13, 0, 0))
        .build();
  }

  @Test
  void normalizesCountryAndKeepsStoredRanksAndMissingMetadata() {
    when(charts.findByCountryCodeAndWeekStart("KR", WEEK)).thenReturn(Optional.of(chart));
    when(entries.findTop5ByChart_ChartIdOrderByGameRankAsc(10L)).thenReturn(List.of(
        entry(101L, 1, 3, "첫 스냅샷"), entry(102L, 2, 7, "누락 게임 스냅샷"),
        entry(103L, 4, 11, "빈 이름 스냅샷")));
    when(games.findAllById(List.of(101L, 102L, 103L))).thenReturn(List.of(
        Game.builder().appId(103L).name(" ").build(),
        Game.builder().appId(101L).name("현재 게임 이름").genre("RPG").priceInfo("10000 KRW")
            .thumbnailUrl("https://example.invalid/game.jpg").minSpecRawText("최소 사양")
            .recSpecRawText("권장 사양").build()));

    WeeklyRankResponse response = service.getWeeklyRank(" kr ", WEEK);

    assertEquals("KR", response.countryCode());
    assertEquals(WEEK, response.weekStart());
    assertEquals(WEEK.plusDays(7), response.weekEndExclusive());
    assertEquals("UNVERIFIED", response.verificationStatus());
    assertEquals(chart.getSourceUrl(), response.sourceUrl());
    assertEquals(chart.getFetchedAt(), response.fetchedAt());
    assertEquals(List.of(1, 2, 4), response.games().stream().map(item -> item.gameRank()).toList());
    assertEquals(List.of(3, 7, 11), response.games().stream().map(item -> item.steamRank()).toList());
    assertEquals("현재 게임 이름", response.games().get(0).name());
    assertEquals("RPG", response.games().get(0).genre());
    assertEquals("최소 사양", response.games().get(0).minSpecRawText());
    assertEquals("누락 게임 스냅샷", response.games().get(1).name());
    assertNull(response.games().get(1).genre());
    assertNull(response.games().get(1).priceInfo());
    assertNull(response.games().get(1).thumbnailUrl());
    assertNull(response.games().get(1).lastWeekRank());
    assertNull(response.games().get(1).consecutiveWeeks());
    assertEquals("빈 이름 스냅샷", response.games().get(2).name());
    verify(charts).findByCountryCodeAndWeekStart("KR", WEEK);
    verify(entries).findTop5ByChart_ChartIdOrderByGameRankAsc(10L);
    verify(games).findAllById(List.of(101L, 102L, 103L));
    verifyNoMoreInteractions(charts, entries, games);
  }

  @Test
  void missingChartDoesNotInventRanksOrLookUpOtherWeeks() {
    when(charts.findByCountryCodeAndWeekStart("KR", WEEK)).thenReturn(Optional.empty());
    assertThrows(WeeklyRankNotFoundException.class, () -> service.getWeeklyRank("KR", WEEK));
    verify(charts).findByCountryCodeAndWeekStart("KR", WEEK);
    verifyNoMoreInteractions(charts);
    verifyNoInteractions(entries, games);
  }

  @Test
  void savedChartWithoutEntriesReturnsEmptyListWithoutFillingFivePlaces() {
    when(charts.findByCountryCodeAndWeekStart("KR", WEEK)).thenReturn(Optional.of(chart));
    when(entries.findTop5ByChart_ChartIdOrderByGameRankAsc(10L)).thenReturn(List.of());
    assertTrue(service.getWeeklyRank("KR", WEEK).games().isEmpty());
    verifyNoInteractions(games);
  }

  @Test
  void rejectsInvalidCountryBeforeDatabaseAccess() {
    for (String country : new String[] {null, "", " ", "K", "KOR", "12", "한글", "K R", "ß"}) {
      assertThrows(InvalidRequestException.class, () -> service.getWeeklyRank(country, WEEK));
    }
    verifyNoInteractions(charts, entries, games);
  }

  @Test
  void rejectsMissingWeekBeforeDatabaseAccess() {
    assertThrows(InvalidRequestException.class, () -> service.getWeeklyRank("KR", null));
    verifyNoInteractions(charts, entries, games);
  }

  private WeeklyRankEntry entry(long appId, int gameRank, int steamRank, String snapshot) {
    return WeeklyRankEntry.builder().chart(chart).appId(appId).gameRank(gameRank)
        .steamRank(steamRank).gameNameSnapshot(snapshot).build();
  }
}
