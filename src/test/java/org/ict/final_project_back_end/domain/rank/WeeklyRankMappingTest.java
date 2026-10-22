package org.ict.final_project_back_end.domain.rank;

import org.hibernate.cfg.Configuration;
import org.ict.final_project_back_end.domain.rank.entity.WeeklyRankChart;
import org.ict.final_project_back_end.domain.rank.entity.WeeklyRankEntry;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WeeklyRankMappingTest {

  @Test
  void validatesAgainstPreexistingSchemaAndPersistsWithoutGamesTable() throws Exception {
    // 운영 DB에는 연결하지 않는다. 주어진 DDL만 별도 메모리 H2에 먼저 만들고 JPA는 validate만 한다.
    String url = "jdbc:h2:mem:rank-mapping-" + UUID.randomUUID() + ";MODE=MySQL";
    try (var connection = DriverManager.getConnection(url, "sa", "");
         var sql = connection.createStatement()) {
      sql.execute("""
          CREATE TABLE rank_weekly_charts (
            chart_id BIGINT AUTO_INCREMENT PRIMARY KEY,
            country_code CHAR(2) NOT NULL,
            week_start DATE NOT NULL,
            week_end_exclusive DATE NOT NULL CHECK (week_end_exclusive > week_start),
            source_type VARCHAR(30) NOT NULL,
            verification_status VARCHAR(20) NOT NULL,
            source_url VARCHAR(500),
            fetched_at DATETIME(6),
            UNIQUE (country_code, week_start)
          )
          """);
      sql.execute("""
          CREATE TABLE rank_weekly_entries (
            rank_id BIGINT AUTO_INCREMENT PRIMARY KEY,
            chart_id BIGINT NOT NULL REFERENCES rank_weekly_charts(chart_id),
            app_id BIGINT NOT NULL,
            game_name_snapshot VARCHAR(500) NOT NULL,
            steam_rank INT NOT NULL,
            game_rank TINYINT NOT NULL CHECK (game_rank BETWEEN 1 AND 10),
            last_week_rank INT,
            consecutive_weeks INT,
            image_url_snapshot TEXT,
            rank_change_kind VARCHAR(16),
            UNIQUE (chart_id, app_id),
            UNIQUE (chart_id, game_rank)
          )
          """);
      Configuration configuration = new Configuration()
          .addAnnotatedClass(WeeklyRankChart.class)
          .addAnnotatedClass(WeeklyRankEntry.class)
          .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
          .setProperty("hibernate.connection.url", url)
          .setProperty("hibernate.connection.username", "sa")
          .setProperty("hibernate.connection.password", "")
          .setProperty("hibernate.hbm2ddl.auto", "validate");

      try (var factory = configuration.buildSessionFactory()) {
        var fetchedAt = LocalDateTime.of(2026, 10, 13, 12, 30, 1, 123456000);
        var chart = WeeklyRankChart.builder().countryCode("KR").weekStart(LocalDate.of(2026, 10, 6))
            .weekEndExclusive(LocalDate.of(2026, 10, 13)).sourceType("TEST_FIXTURE")
            .verificationStatus("UNVERIFIED").sourceUrl("https://example.invalid/" + "x".repeat(476))
            .fetchedAt(fetchedAt).build();
        var entry = WeeklyRankEntry.builder().chart(chart).appId(9000000001L).gameRank(10).steamRank(30)
            .gameNameSnapshot("가".repeat(500))
            .imageUrlSnapshot("https://example.invalid/" + "x".repeat(600))
            .rankChangeKind("RETURNING").build();
        factory.inTransaction(session -> {
          session.persist(chart);
          session.persist(entry);
        });
        assertNotNull(chart.getChartId());
        assertNotNull(entry.getRankId());
        factory.inTransaction(session -> {
          var loaded = session.find(WeeklyRankEntry.class, entry.getRankId());
          assertEquals(9000000001L, loaded.getAppId());
          assertEquals(10, loaded.getGameRank());
          assertEquals(30, loaded.getSteamRank());
          assertEquals("가".repeat(500), loaded.getGameNameSnapshot());
          assertEquals(entry.getImageUrlSnapshot(), loaded.getImageUrlSnapshot());
          assertEquals("RETURNING", loaded.getRankChangeKind());
          assertNull(loaded.getLastWeekRank());
          assertNull(loaded.getConsecutiveWeeks());
          assertEquals("KR", loaded.getChart().getCountryCode());
          assertEquals(fetchedAt, loaded.getChart().getFetchedAt());
          assertEquals(chart.getSourceUrl(), loaded.getChart().getSourceUrl());
        });

        var nullableEntry = WeeklyRankEntry.builder().chart(chart).appId(9000000002L)
            .gameRank(6).steamRank(6).gameNameSnapshot("No optional metadata").build();
        factory.inTransaction(session -> session.persist(nullableEntry));
        factory.inTransaction(session -> {
          var loaded = session.find(WeeklyRankEntry.class, nullableEntry.getRankId());
          assertNull(loaded.getImageUrlSnapshot());
          assertNull(loaded.getRankChangeKind());
        });

        long chartId = chart.getChartId();
        assertThrows(SQLException.class, () -> sql.execute(
            "INSERT INTO rank_weekly_charts (country_code, week_start, week_end_exclusive, source_type, verification_status) VALUES ('KR', DATE '2026-10-06', DATE '2026-10-13', 'TEST_FIXTURE', 'UNVERIFIED')"));
        assertThrows(SQLException.class, () -> sql.execute(
            "INSERT INTO rank_weekly_entries (chart_id, app_id, game_name_snapshot, steam_rank, game_rank) VALUES ("
                + chartId + ", 9000000001, 'duplicate app', 1, 1)"));
        assertThrows(SQLException.class, () -> sql.execute(
            "INSERT INTO rank_weekly_entries (chart_id, app_id, game_name_snapshot, steam_rank, game_rank) VALUES ("
                + chartId + ", 2, 'duplicate rank', 1, 10)"));
        for (int invalidRank : new int[] {0, 11}) {
          assertThrows(SQLException.class, () -> sql.execute(
              "INSERT INTO rank_weekly_entries (chart_id, app_id, game_name_snapshot, steam_rank, game_rank) VALUES ("
                  + chartId + ", 2, 'invalid rank', 1, " + invalidRank + ")"));
        }
        assertThrows(SQLException.class, () -> sql.execute(
            "INSERT INTO rank_weekly_entries (chart_id, app_id, game_name_snapshot, steam_rank, game_rank) VALUES (-1, 2, 'invalid chart', 1, 1)"));
      }
    }
  }
}
