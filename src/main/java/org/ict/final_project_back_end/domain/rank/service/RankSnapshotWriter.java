package org.ict.final_project_back_end.domain.rank.service;

import org.ict.final_project_back_end.domain.rank.dto.SteamWeeklySnapshot;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** Only the requested snapshots are replaced; any failure rolls back both countries. */
@Service
public class RankSnapshotWriter {
  private final JdbcTemplate jdbc;
  public RankSnapshotWriter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  static void validatePair(List<SteamWeeklySnapshot> snapshots) {
    if (snapshots == null || snapshots.size() != 2 || !"KR".equals(snapshots.get(0).country())
        || !"US".equals(snapshots.get(1).country()) || !snapshots.get(0).week().equals(snapshots.get(1).week())) {
      throw new IllegalArgumentException("Matching KR and US snapshots are required in that order");
    }
  }

  @Transactional
  public void savePair(List<SteamWeeklySnapshot> snapshots) {
    validatePair(snapshots);
    for (var snapshot : snapshots) save(snapshot);
  }

  private void save(SteamWeeklySnapshot s) {
    var fetched = LocalDateTime.ofInstant(s.fetchedAt(), ZoneOffset.UTC);
    var existing = jdbc.query("""
        SELECT chart_id, fetched_at FROM rank_weekly_charts
        WHERE country_code = ? AND week_start = ? FOR UPDATE
        """, (rs, n) -> new ExistingChart(rs.getLong(1), rs.getTimestamp(2) == null
            ? null : rs.getTimestamp(2).toLocalDateTime()), s.country(), s.week());
    long chartId;
    if (existing.isEmpty()) {
      var key = new GeneratedKeyHolder();
      jdbc.update(connection -> {
        var statement = connection.prepareStatement("""
            INSERT INTO rank_weekly_charts
            (country_code, week_start, week_end_exclusive, source_type, verification_status, source_url, fetched_at)
            VALUES (?, ?, ?, 'STEAM_WEEKLY', ?, ?, ?)
            """, Statement.RETURN_GENERATED_KEYS);
        statement.setString(1, s.country());
        statement.setObject(2, s.week());
        statement.setObject(3, s.week().plusDays(7));
        statement.setString(4, s.status());
        statement.setString(5, s.sourceUrl());
        statement.setTimestamp(6, Timestamp.valueOf(fetched));
        return statement;
      }, key);
      if (key.getKey() == null) throw new IllegalStateException("Missing generated chart ID");
      chartId = key.getKey().longValue();
    } else {
      var chart = existing.getFirst();
      if (chart.fetchedAt() != null && fetched.isBefore(chart.fetchedAt())) {
        throw new IllegalStateException("Refusing to overwrite a more recently fetched chart");
      }
      chartId = chart.id();
      jdbc.update("""
          UPDATE rank_weekly_charts SET week_end_exclusive = ?, source_type = 'STEAM_WEEKLY',
          verification_status = ?, source_url = ?, fetched_at = ? WHERE chart_id = ?
          """, s.week().plusDays(7), s.status(), s.sourceUrl(), fetched, chartId);
      jdbc.update("DELETE FROM rank_weekly_entries WHERE chart_id = ?", chartId);
    }
    for (var p : s.products()) {
      jdbc.update("""
          INSERT INTO rank_weekly_entries
          (chart_id, app_id, game_name_snapshot, steam_rank, game_rank, last_week_rank,
           consecutive_weeks, image_url_snapshot, rank_change_kind) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
          """, chartId, p.appId(), p.name(), p.rank(), p.rank(), p.previousRank(),
          p.consecutiveWeeks(), p.imageUrl(), p.changeKind());
    }
  }

  private record ExistingChart(long id, LocalDateTime fetchedAt) {}
}
