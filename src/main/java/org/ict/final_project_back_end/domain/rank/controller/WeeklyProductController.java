package org.ict.final_project_back_end.domain.rank.controller;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Read-only archive of original Steam products (including hardware).
 * Uses stored product snapshots; the metadata endpoint is kept separately.
 */
@RestController
public class WeeklyProductController {
  private final JdbcTemplate jdbc;

  public WeeklyProductController(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public record Option(String country, LocalDate week, String status) {}
  public record Product(long appId, String name, int sourceRank, Integer previousRank,
                        Integer consecutiveWeeks, String imageUrl, String changeKind) {}
  public record Snapshot(String country, LocalDate week, String status, String sourceUrl,
                         String verifiedAt, List<Product> entries) {}

  @GetMapping("/api/ranks/weekly/options")
  public List<Option> options() {
    return jdbc.query("""
        SELECT country_code, week_start, verification_status FROM rank_weekly_charts
        ORDER BY week_start DESC, country_code
        """, (rs, n) -> new Option(rs.getString(1), rs.getDate(2).toLocalDate(), rs.getString(3)));
  }

  @GetMapping("/api/ranks/weekly/products")
  public Snapshot products(@RequestParam String country,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week) {
    if (!country.matches("[a-zA-Z]{2}") || week.getDayOfWeek() != DayOfWeek.TUESDAY) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid country or chart week");
    }
    String code = country.toUpperCase(Locale.ROOT);
    var charts = jdbc.query("""
        SELECT chart_id, verification_status, source_url, fetched_at
        FROM rank_weekly_charts WHERE country_code = ? AND week_start = ?
        """, (rs, n) -> new Object[] {rs.getLong(1), rs.getString(2), rs.getString(3),
            rs.getTimestamp(4) == null ? null : rs.getTimestamp(4).toLocalDateTime().toString() + "Z"},
        code, week);
    if (charts.isEmpty()) {
      // Preserve country-level unavailability even when an uncollected week is selected.
      var states = jdbc.query("""
          SELECT verification_status FROM rank_weekly_charts WHERE country_code = ?
          ORDER BY week_start DESC LIMIT 1
          """, (rs, n) -> rs.getString(1), code);
      String status = !states.isEmpty() && "COUNTRY_UNAVAILABLE".equals(states.getFirst())
          ? "COUNTRY_UNAVAILABLE" : "NOT_COLLECTED";
      return new Snapshot(code, week, status, null, null, List.of());
    }
    var chart = charts.getFirst();
    String status = (String) chart[1];
    List<Product> entries = List.of();
    if ("VERIFIED".equals(status) || "VERIFIED_GAPS".equals(status)) {
      entries = jdbc.query("""
          SELECT app_id, game_name_snapshot, steam_rank, last_week_rank,
                 consecutive_weeks, image_url_snapshot, rank_change_kind
          FROM rank_weekly_entries WHERE chart_id = ? ORDER BY steam_rank LIMIT 10
          """, (rs, n) -> new Product(rs.getLong(1), rs.getString(2), rs.getInt(3),
              rs.getObject(4, Integer.class), rs.getObject(5, Integer.class),
              rs.getString(6), rs.getString(7)), chart[0]);
    }
    return new Snapshot(code, week, status, (String) chart[2], (String) chart[3], entries);
  }
}
