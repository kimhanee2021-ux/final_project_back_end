package org.ict.final_project_back_end.domain.rank;

import jakarta.persistence.EntityManager;
import org.ict.final_project_back_end.domain.game.entity.Game;
import org.ict.final_project_back_end.domain.game.repository.GameRepository;
import org.ict.final_project_back_end.domain.rank.entity.WeeklyRankChart;
import org.ict.final_project_back_end.domain.rank.entity.WeeklyRankEntry;
import org.ict.final_project_back_end.domain.rank.repository.WeeklyRankChartRepository;
import org.ict.final_project_back_end.domain.rank.repository.WeeklyRankEntryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// 테스트 전용 H2 데이터만 사용하며 기존 게임 수집 러너/스케줄러도 실행하지 않는다.
@SpringBootTest(properties = {
    "app.featured.sync-on-startup=false", "app.featured.sync-cron=-",
    "app.seed.games.enabled=false", "app.seed.prices.enabled=false"
})
@AutoConfigureMockMvc
@Transactional
@WithMockUser
class RankApiIntegrationTest {

  private static final LocalDate WEEK = LocalDate.of(2026, 10, 6);
  @Autowired private MockMvc mvc;
  @Autowired private WeeklyRankChartRepository charts;
  @Autowired private WeeklyRankEntryRepository entries;
  @Autowired private GameRepository games;
  @Autowired private EntityManager entityManager;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void returnsTopFiveInStoredGameRankOrderEvenWithoutGameMetadata() throws Exception {
    WeeklyRankChart chart = chart("KR", WEEK);
    for (int rank : new int[] {5, 3, 1, 4, 2}) {
      entry(chart, 9000000000L + rank, rank, rank * 3);
    }
    games.save(Game.builder().appId(9000000001L).name("메타데이터 이름")
        .genre("RPG").priceInfo("10000 KRW").thumbnailUrl("https://example.invalid/game.jpg")
        .minSpecRawText("최소 사양").recSpecRawText("권장 사양").build());
    entry(chart("US", WEEK), 9100000001L, 1, 1);
    entry(chart("KR", WEEK.minusDays(7)), 9200000001L, 1, 1);
    flushAndClear();
    long gameCount = games.count();

    mvc.perform(get("/api/ranks/weekly").param("country", " kr ").param("week", "2026-10-06"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.chartId").value(chart.getChartId()))
        .andExpect(jsonPath("$.countryCode").value("KR"))
        .andExpect(jsonPath("$.weekStart").value("2026-10-06"))
        .andExpect(jsonPath("$.weekEndExclusive").value("2026-10-13"))
        .andExpect(jsonPath("$.sourceType").value("TEST_FIXTURE"))
        .andExpect(jsonPath("$.verificationStatus").value("UNVERIFIED"))
        .andExpect(jsonPath("$.sourceUrl").value("https://example.invalid/rank-test"))
        .andExpect(jsonPath("$.games.length()").value(5))
        .andExpect(jsonPath("$.games[0].appId").value(9000000001L))
        .andExpect(jsonPath("$.games[0].name").value("메타데이터 이름"))
        .andExpect(jsonPath("$.games[0].genre").value("RPG"))
        .andExpect(jsonPath("$.games[0].priceInfo").value("10000 KRW"))
        .andExpect(jsonPath("$.games[0].minSpecRawText").value("최소 사양"))
        .andExpect(jsonPath("$.games[0].recSpecRawText").value("권장 사양"))
        .andExpect(jsonPath("$.games[0].gameRank").value(1))
        .andExpect(jsonPath("$.games[0].steamRank").value(3))
        .andExpect(jsonPath("$.games[1].name").value("테스트 스냅샷 2"))
        .andExpect(jsonPath("$.games[1].gameRank").value(2))
        .andExpect(jsonPath("$.games[1].steamRank").value(6))
        .andExpect(jsonPath("$.games[1].thumbnailUrl").doesNotExist())
        .andExpect(jsonPath("$.games[4].gameRank").value(5))
        .andExpect(jsonPath("$.games[4].steamRank").value(15))
        .andExpect(jsonPath("$.games[4].lastWeekRank").value(20))
        .andExpect(jsonPath("$.games[4].consecutiveWeeks").value(2));
    assertEquals(gameCount, games.count());
    assertFalse(games.existsById(9000000002L));
    assertEquals(7, entries.count());
  }

  @Test
  void missingCountryOrExactWeekReturns404WithoutFallback() throws Exception {
    entry(chart("KR", WEEK.minusDays(7)), 1L, 1, 1);
    entry(chart("US", WEEK), 2L, 1, 1);
    flushAndClear();

    mvc.perform(get("/api/ranks/weekly").param("country", "KR").param("week", "2026-10-06"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("WEEKLY_RANK_NOT_FOUND"))
        .andExpect(jsonPath("$.games").doesNotExist());
    assertEquals(2, charts.count());
    assertEquals(2, entries.count());
  }

  @Test
  void savedEmptyChartReturnsEmptyGames() throws Exception {
    chart("KR", WEEK);
    flushAndClear();
    mvc.perform(get("/api/ranks/weekly").param("country", "KR").param("week", "2026-10-06"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.games.length()").value(0));
  }

  @Test
  void incompleteChartKeepsGapsAndNullableHistory() throws Exception {
    var chart = chart("KR", WEEK);
    entries.save(WeeklyRankEntry.builder().chart(chart).appId(123L).gameRank(3).steamRank(12)
        .gameNameSnapshot("저장된 한 게임").build());
    flushAndClear();
    mvc.perform(get("/api/ranks/weekly").param("country", "KR").param("week", "2026-10-06"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.games.length()").value(1))
        .andExpect(jsonPath("$.games[0].gameRank").value(3))
        .andExpect(jsonPath("$.games[0].steamRank").value(12))
        .andExpect(jsonPath("$.games[0].lastWeekRank").doesNotExist())
        .andExpect(jsonPath("$.games[0].consecutiveWeeks").doesNotExist());
  }

  @Test
  void rejectsInvalidCountryDateAndMissingParameters() throws Exception {
    for (String country : new String[] {"", "KOR", "12", "한글"}) {
      mvc.perform(get("/api/ranks/weekly").param("country", country).param("week", "2026-10-06"))
          .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
    for (String week : new String[] {"", "invalid", "2026-02-30", "2026-13-06"}) {
      mvc.perform(get("/api/ranks/weekly").param("country", "KR").param("week", week))
          .andExpect(status().isBadRequest());
    }
    mvc.perform(get("/api/ranks/weekly").param("country", "KR")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/ranks/weekly").param("week", "2026-10-06")).andExpect(status().isBadRequest());
  }

  @Test
  @WithAnonymousUser
  void preservesExistingApiAuthenticationPolicy() throws Exception {
    mvc.perform(get("/api/ranks/weekly").param("country", "KR").param("week", "2026-10-06"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void generatedMappingHasChartForeignKeyButNoGamesForeignKey() {
    var referencedTables = jdbc.queryForList("""
        SELECT target.TABLE_NAME
        FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS fk
        JOIN INFORMATION_SCHEMA.TABLE_CONSTRAINTS owner
          ON owner.CONSTRAINT_SCHEMA = fk.CONSTRAINT_SCHEMA AND owner.CONSTRAINT_NAME = fk.CONSTRAINT_NAME
        JOIN INFORMATION_SCHEMA.TABLE_CONSTRAINTS target
          ON target.CONSTRAINT_SCHEMA = fk.UNIQUE_CONSTRAINT_SCHEMA
          AND target.CONSTRAINT_NAME = fk.UNIQUE_CONSTRAINT_NAME
        WHERE owner.TABLE_NAME = 'RANK_WEEKLY_ENTRIES'
        """, String.class);
    assertEquals(java.util.List.of("RANK_WEEKLY_CHARTS"), referencedTables);
  }

  private WeeklyRankChart chart(String country, LocalDate week) {
    return charts.save(WeeklyRankChart.builder().countryCode(country).weekStart(week)
        .weekEndExclusive(week.plusDays(7)).sourceType("TEST_FIXTURE").verificationStatus("UNVERIFIED")
        .sourceUrl("https://example.invalid/rank-test").fetchedAt(LocalDateTime.of(2026, 10, 13, 12, 0))
        .build());
  }

  private void entry(WeeklyRankChart chart, long appId, int gameRank, int steamRank) {
    entries.save(WeeklyRankEntry.builder().chart(chart).appId(appId).gameRank(gameRank).steamRank(steamRank)
        .gameNameSnapshot("테스트 스냅샷 " + gameRank).lastWeekRank(20).consecutiveWeeks(2).build());
  }

  private void flushAndClear() {
    entityManager.flush();
    entityManager.clear();
  }
}
