package org.ict.final_project_back_end.domain.rank.service;

import org.ict.final_project_back_end.domain.rank.dto.SteamWeeklySnapshot;
import org.ict.final_project_back_end.domain.rank.dto.SteamWeeklySnapshot.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;
import java.time.*;
import java.util.*;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

/** Separate H2 datasource only. No Spring Boot main, .env, MySQL or schedulers are loaded. */
@SpringJUnitConfig(RankSnapshotWriterTest.Config.class)
class RankSnapshotWriterTest {
  private static final LocalDate WEEK = LocalDate.of(2026,10,13);
  private static final Instant FETCHED = Instant.parse("2026-10-21T05:00:00Z");
  @Autowired JdbcTemplate jdbc;
  @Autowired RankSnapshotWriter writer;

  @Configuration
  @EnableTransactionManagement
  static class Config {
    @Bean DataSource dataSource() {
      return new DriverManagerDataSource("jdbc:h2:mem:rank-sync-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
    }
    @Bean JdbcTemplate jdbc(DataSource ds) { return new JdbcTemplate(ds); }
    @Bean PlatformTransactionManager transactions(DataSource ds) { return new DataSourceTransactionManager(ds); }
    @Bean RankSnapshotWriter writer(JdbcTemplate jdbc) { return new RankSnapshotWriter(jdbc); }
  }

  @BeforeEach
  void schema() {
    jdbc.execute("""
        CREATE TABLE IF NOT EXISTS rank_weekly_charts (
          chart_id BIGINT AUTO_INCREMENT PRIMARY KEY, country_code CHAR(2) NOT NULL,
          week_start DATE NOT NULL, week_end_exclusive DATE NOT NULL,
          source_type VARCHAR(30) NOT NULL, verification_status VARCHAR(20) NOT NULL,
          source_url VARCHAR(500), fetched_at DATETIME(6),
          UNIQUE(country_code,week_start), CHECK(week_end_exclusive > week_start))
        """);
    jdbc.execute("""
        CREATE TABLE IF NOT EXISTS rank_weekly_entries (
          rank_id BIGINT AUTO_INCREMENT PRIMARY KEY,
          chart_id BIGINT NOT NULL REFERENCES rank_weekly_charts(chart_id),
          app_id BIGINT NOT NULL, game_name_snapshot VARCHAR(500) NOT NULL,
          steam_rank INT NOT NULL, game_rank TINYINT NOT NULL CHECK(game_rank BETWEEN 1 AND 10),
          last_week_rank INT, consecutive_weeks INT, image_url_snapshot TEXT, rank_change_kind VARCHAR(16),
          UNIQUE(chart_id,app_id), UNIQUE(chart_id,game_rank),
          CONSTRAINT simulated_write_failure CHECK(game_name_snapshot <> 'FAIL'))
        """);
    jdbc.update("DELETE FROM rank_weekly_entries");jdbc.update("DELETE FROM rank_weekly_charts");
  }

  @Test
  void storesBothCountriesAndRepeatedSyncDoesNotDuplicate() {
    var pair=pair(FETCHED,"original");writer.savePair(pair);
    assertEquals(2, count("rank_weekly_charts"));assertEquals(20,count("rank_weekly_entries"));
    assertEquals(List.of(10,10), jdbc.queryForList("SELECT COUNT(*) FROM rank_weekly_entries GROUP BY chart_id",Integer.class));
    assertEquals("VERIFIED",jdbc.queryForObject("SELECT verification_status FROM rank_weekly_charts WHERE country_code='KR'",String.class));
    var ids=jdbc.queryForList("SELECT chart_id FROM rank_weekly_charts ORDER BY country_code",Long.class);
    writer.savePair(pair);
    assertEquals(2,count("rank_weekly_charts"));assertEquals(20,count("rank_weekly_entries"));
    assertEquals(ids,jdbc.queryForList("SELECT chart_id FROM rank_weekly_charts ORDER BY country_code",Long.class));
    assertEquals("https://shared.fastly.steamstatic.com/test.jpg",jdbc.queryForObject("SELECT image_url_snapshot FROM rank_weekly_entries WHERE chart_id=? AND game_rank=10",String.class,ids.getFirst()));
    assertEquals("SAME",jdbc.queryForObject("SELECT rank_change_kind FROM rank_weekly_entries WHERE chart_id=? AND game_rank=10",String.class,ids.getFirst()));
  }

  @Test
  void updatesUnavailableWeekAndLeavesOtherWeeksAlone() {
    seed("KR",WEEK,"WEEK_UNAVAILABLE");seed("US",WEEK,"WEEK_UNAVAILABLE");
    seed("KR",WEEK.minusDays(7),"WEEK_UNAVAILABLE");
    var before=jdbc.queryForMap("SELECT * FROM rank_weekly_charts WHERE week_start=?",WEEK.minusDays(7));
    writer.savePair(pair(FETCHED,"new"));
    assertEquals(3,count("rank_weekly_charts"));assertEquals(20,count("rank_weekly_entries"));
    assertEquals(before,jdbc.queryForMap("SELECT * FROM rank_weekly_charts WHERE week_start=?",WEEK.minusDays(7)));
    assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM rank_weekly_charts WHERE verification_status='VERIFIED'",Integer.class));
  }

  @Test
  void secondCountryInsertFailureRollsBackNewChartsAndEntries() {
    var us=failingUs(FETCHED);
    assertThrows(RuntimeException.class,() -> writer.savePair(List.of(snapshot("KR",FETCHED,"new"),us)));
    assertEquals(0,count("rank_weekly_charts"));assertEquals(0,count("rank_weekly_entries"));
  }

  @Test
  void failedRefreshRestoresBothExistingCountriesIncludingImagesAndTimestamps() {
    writer.savePair(pair(FETCHED,"original"));
    var charts=jdbc.queryForList("SELECT * FROM rank_weekly_charts ORDER BY chart_id");
    var entries=jdbc.queryForList("SELECT * FROM rank_weekly_entries ORDER BY rank_id");
    Instant later=FETCHED.plusSeconds(60);
    assertThrows(RuntimeException.class,() -> writer.savePair(List.of(snapshot("KR",later,"replacement"),failingUs(later))));
    assertEquals(charts,jdbc.queryForList("SELECT * FROM rank_weekly_charts ORDER BY chart_id"));
    assertEquals(entries,jdbc.queryForList("SELECT * FROM rank_weekly_entries ORDER BY rank_id"));
  }

  @Test
  void olderResponseCannotOverwriteNewerData() {
    writer.savePair(pair(FETCHED,"original"));
    var before=jdbc.queryForList("SELECT * FROM rank_weekly_entries ORDER BY rank_id");
    assertThrows(IllegalStateException.class,() -> writer.savePair(List.of(
        snapshot("KR",FETCHED.plusSeconds(60),"new"),snapshot("US",FETCHED.minusSeconds(60),"old"))));
    assertEquals(before,jdbc.queryForList("SELECT * FROM rank_weekly_entries ORDER BY rank_id"));
  }

  @Test
  void missingOptionalMetadataKeepsTenProductsWithGapStatus() {
    writer.savePair(List.of(RankSyncServiceTest.snapshot("KR",WEEK),RankSyncServiceTest.snapshot("US",WEEK)));
    assertEquals(20,count("rank_weekly_entries"));
    assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM rank_weekly_charts WHERE verification_status='VERIFIED_GAPS'",Integer.class));
    assertEquals(20,jdbc.queryForObject("SELECT COUNT(*) FROM rank_weekly_entries WHERE image_url_snapshot IS NULL",Integer.class));
  }

  @Test
  void rejectsMismatchedPairBeforeWriting() {
    assertThrows(IllegalArgumentException.class,() -> writer.savePair(List.of(
        snapshot("KR",FETCHED,"new"),RankSyncServiceTest.snapshot("US",WEEK.minusDays(7)))));
    assertEquals(0,count("rank_weekly_charts"));
  }

  @Test
  void storesNewAndReturningInIsolatedH2Only() {
    var products = new ArrayList<>(snapshot("US", FETCHED, "sample").products());
    products.set(0, new Product(1,1001,"new",null,1,"https://shared.akamai.steamstatic.com/test.jpg",true));
    products.set(1, new Product(2,1002,"returning",154,1,"https://shared.akamai.steamstatic.com/test.jpg",false));
    writer.savePair(List.of(snapshot("KR",FETCHED,"sample"), new SteamWeeklySnapshot("US",WEEK,FETCHED,products)));
    assertEquals(List.of("NEW", "RETURNING"), jdbc.queryForList("SELECT rank_change_kind FROM rank_weekly_entries e JOIN rank_weekly_charts c ON c.chart_id=e.chart_id WHERE c.country_code='US' AND game_rank<=2 ORDER BY game_rank",String.class));
    assertEquals("VERIFIED",jdbc.queryForObject("SELECT verification_status FROM rank_weekly_charts WHERE country_code='US'",String.class));
  }

  @Test
  void scheduledFetchFailurePreservesExistingDataAndLaterRetrySucceeds() {
    writer.savePair(pair(FETCHED,"original"));
    seed("KR",WEEK.minusDays(7),"WEEK_UNAVAILABLE");
    var charts=jdbc.queryForList("SELECT * FROM rank_weekly_charts ORDER BY chart_id");
    var entries=jdbc.queryForList("SELECT * FROM rank_weekly_entries ORDER BY rank_id");
    var client=org.mockito.Mockito.mock(org.ict.final_project_back_end.domain.rank.client.SteamRankClient.class);
    var scheduler=new RankSyncScheduler(new RankSyncService(client,writer,true));
    org.mockito.Mockito.when(client.fetchWeeklyRank("KR",null)).thenReturn(snapshot("KR",FETCHED.plusSeconds(60),"updated"));
    org.mockito.Mockito.when(client.fetchWeeklyRank("US",WEEK)).thenThrow(new IllegalStateException("테스트 미국 조회 실패"));
    scheduler.collectLatest();
    assertEquals(charts,jdbc.queryForList("SELECT * FROM rank_weekly_charts ORDER BY chart_id"));
    assertEquals(entries,jdbc.queryForList("SELECT * FROM rank_weekly_entries ORDER BY rank_id"));
    org.mockito.Mockito.doReturn(snapshot("US",FETCHED.plusSeconds(60),"updated")).when(client).fetchWeeklyRank("US",WEEK);
    scheduler.collectLatest();
    assertEquals(20,count("rank_weekly_entries"));
    assertEquals(20,jdbc.queryForObject("SELECT COUNT(*) FROM rank_weekly_entries WHERE game_name_snapshot LIKE 'updated%'",Integer.class));
    assertEquals(charts.stream().filter(r -> r.get("week_start").toString().equals(WEEK.minusDays(7).toString())).toList(),
        jdbc.queryForList("SELECT * FROM rank_weekly_charts WHERE week_start=? ORDER BY chart_id",WEEK.minusDays(7)));
  }

  @Test
  void scheduledWriteFailureRollsBackBothCountriesBeforeErrorIsHandled() {
    writer.savePair(pair(FETCHED,"original"));
    var charts=jdbc.queryForList("SELECT * FROM rank_weekly_charts ORDER BY chart_id");
    var entries=jdbc.queryForList("SELECT * FROM rank_weekly_entries ORDER BY rank_id");
    var client=org.mockito.Mockito.mock(org.ict.final_project_back_end.domain.rank.client.SteamRankClient.class);
    org.mockito.Mockito.when(client.fetchWeeklyRank("KR",null)).thenReturn(snapshot("KR",FETCHED.plusSeconds(60),"replacement"));
    org.mockito.Mockito.when(client.fetchWeeklyRank("US",WEEK)).thenReturn(failingUs(FETCHED.plusSeconds(60)));
    assertDoesNotThrow(() -> new RankSyncScheduler(new RankSyncService(client,writer,true)).collectLatest());
    assertEquals(charts,jdbc.queryForList("SELECT * FROM rank_weekly_charts ORDER BY chart_id"));
    assertEquals(entries,jdbc.queryForList("SELECT * FROM rank_weekly_entries ORDER BY rank_id"));
  }

  private void seed(String country,LocalDate week,String status) {
    jdbc.update("INSERT INTO rank_weekly_charts(country_code,week_start,week_end_exclusive,source_type,verification_status) VALUES(?,?,?,'STEAM_WEEKLY',?)",country,week,week.plusDays(7),status);
  }
  private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table,Integer.class); }
  private List<SteamWeeklySnapshot> pair(Instant time,String name) { return List.of(snapshot("KR",time,name),snapshot("US",time,name)); }
  private SteamWeeklySnapshot snapshot(String country,Instant time,String name) {
    return new SteamWeeklySnapshot(country,WEEK,time,IntStream.rangeClosed(1,10)
        .mapToObj(i -> new Product(i,1000L+i,name + i,i,2,"https://shared.fastly.steamstatic.com/test.jpg")).toList());
  }
  private SteamWeeklySnapshot failingUs(Instant time) {
    var products=new ArrayList<>(snapshot("US",time,"new").products());
    products.set(9,new Product(10,1010,"FAIL",10,2,null));
    return new SteamWeeklySnapshot("US",WEEK,time,products);
  }
}
