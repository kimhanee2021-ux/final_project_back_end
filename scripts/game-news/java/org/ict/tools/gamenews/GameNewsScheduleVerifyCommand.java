package org.ict.tools.gamenews;

import io.github.cdimascio.dotenv.Dotenv;
import org.ict.final_project_back_end.domain.gamenews.client.*;
import org.ict.final_project_back_end.domain.gamenews.config.GameNewsSyncConfiguration;
import org.ict.final_project_back_end.domain.gamenews.dto.GameNewsSyncResult.CollectionStatus;
import org.ict.final_project_back_end.domain.gamenews.service.*;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Bounded live verification. Advances only the selected games' next collection time, never fabricates source changes. */
public class GameNewsScheduleVerifyCommand {
  public static void main(String[] args) throws Exception {
    System.setProperty("spring.devtools.restart.enabled", "false");
    if (args.length != 1) throw new IllegalArgumentException("Specify app IDs");
    var ids = Arrays.stream(args[0].split(",", -1)).map(String::strip).map(Long::parseLong).distinct().toList();
    if (ids.isEmpty() || ids.size() > 20) throw new IllegalArgumentException("Specify 1-20 games");
    ids.forEach(id -> SteamNewsClient.validateRequest(id, 5));
    var dotenv = Dotenv.configure().ignoreIfMissing().load();
    for (String key : List.of("DB_URL", "DB_USERNAME", "DB_PASSWORD", "GEMINI_API_KEY", "GEMINI_COACH_MODEL", "GEMINI_BASE_URL")) {
      String value = dotenv.get(key); if (value != null) System.setProperty(key, value);
    }
    try (var context = new SpringApplicationBuilder(VerifyConfig.class).web(WebApplicationType.NONE)
        .properties(Map.of("spring.datasource.url", "${DB_URL}", "spring.datasource.username", "${DB_USERNAME}",
            "spring.datasource.password", "${DB_PASSWORD}", "spring.datasource.driver-class-name", "com.mysql.cj.jdbc.Driver",
            "app.coach.api-key", "${GEMINI_API_KEY}", "app.coach.model", "${GEMINI_COACH_MODEL:gemini-3.5-flash-lite}",
            "app.coach.base-url", "${GEMINI_BASE_URL:https://generativelanguage.googleapis.com}"))
        .run("--spring.config.name=game-news-schedule-verification", "--spring.jpa.hibernate.ddl-auto=validate",
            "--spring.jpa.show-sql=false", "--spring.jpa.open-in-view=false", "--spring.main.banner-mode=off", "--logging.level.root=WARN",
            "--app.news.sync.enabled=true", "--app.news.sync.app-ids=" + String.join(",", ids.stream().map(Object::toString).toList()),
            "--app.news.sync.news-count=5", "--app.news.sync.briefing-batch-size=5",
            "--app.news.sync.initial-delay=PT10S", "--app.news.sync.poll-interval=PT5S")) {
      var db = context.getBean(JdbcTemplate.class);
      for (long id : ids) require(db.queryForObject("select count(*) from games where app_id = ?", Long.class, id) == 1, "Game exists: " + id);
      var before = snapshot(db, ids);
      long gameCount = db.queryForObject("select count(*) from games", Long.class);
      long boardCount = db.queryForObject("select count(*) from board_posts", Long.class);
      // This is an explicit verification action, not part of normal startup or scheduling.
      for (long id : ids) db.update("update game_news_sync_state set next_collect_at = ? where app_id = ?",
          LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1), id);
      var scheduler = context.getBean(GameNewsSyncScheduler.class);
      Instant deadline = Instant.now().plusSeconds(180);
      long seen = 0;
      while (seen < 2 && Instant.now().isBefore(deadline)) {
        if (scheduler.completedRuns() > seen) {
          seen = scheduler.completedRuns();
          var report = scheduler.lastResult();
          require(!report.alreadyRunning() && !report.interrupted() && report.games().size() == ids.size(), "Completed full cycle");
          for (var game : report.games()) {
            var collected = game.collection();
            System.out.printf("cycle=%d app_id=%d collection=%s inserted=%d updated=%d unchanged=%d briefings=%s errors=%d%n",
                seen, game.appId(), game.collectionStatus(), collected == null ? 0 : collected.inserted(),
                collected == null ? 0 : collected.updated(), collected == null ? 0 : collected.unchanged(), game.briefings(), game.briefingErrors());
            require(game.collectionStatus() != CollectionStatus.FAILED && game.briefingErrors() == 0, "No collection/persistence errors");
            if (seen >= 2) require(game.collectionStatus() == CollectionStatus.NOT_DUE && game.briefings().isEmpty(), "Second tick makes no redundant work");
          }
        }
        if (seen < 2) Thread.sleep(250);
      }
      require(seen >= 2, "Two scheduled executions before timeout");
      var after = snapshot(db, ids);
      int preserved = 0;
      for (var entry : after.entrySet()) {
        var row = entry.getValue();
        require("SUCCEEDED".equals(row.get("generation_status")) && Objects.equals(row.get("content_hash"), row.get("generated_from_hash")), "Current successful briefing " + entry.getKey());
        var old = before.get(entry.getKey());
        if (old != null && old.get("generated_at") != null && Objects.equals(old.get("content_hash"), row.get("content_hash"))) {
          for (String field : List.of("ai_content", "summary", "generated_at", "generated_from_hash", "model_name", "prompt_version")) {
            require(Objects.equals(old.get(field), row.get(field)), "Unchanged source preserves " + field);
          }
          preserved++;
        }
      }
      require(gameCount == db.queryForObject("select count(*) from games", Long.class), "Games preserved");
      require(boardCount == db.queryForObject("select count(*) from board_posts", Long.class), "Board preserved");
      System.out.printf("scheduled_cycles=%d current_briefings=%d preserved_unchanged=%d actual_ai_calls=%d%n",
          seen, after.size(), preserved, context.getBean(CountingAi.class).calls.get());
      for (long id : ids) System.out.printf("app_id=%d next_collect_at_utc=%s%n", id,
          db.queryForObject("select next_collect_at from game_news_sync_state where app_id = ?", LocalDateTime.class, id));
    }
  }

  private static Map<Long, Map<String, Object>> snapshot(JdbcTemplate db, List<Long> ids) {
    var result = new LinkedHashMap<Long, Map<String, Object>>();
    for (long id : ids) for (var row : db.queryForList("""
        select n.id, n.content_hash, b.ai_content, b.summary, b.generated_at, b.generated_from_hash,
          b.model_name, b.prompt_version, b.generation_status
        from game_news n left join game_news_briefings b on b.news_id = n.id where n.app_id = ? order by n.id
        """, id)) result.put(((Number) row.get("id")).longValue(), row);
    return result;
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException("Schedule verification failed: " + message);
  }

  static class CountingAi implements NewsBriefingAiClient {
    private final GeminiNewsBriefingClient delegate;
    final AtomicInteger calls = new AtomicInteger();
    CountingAi(GeminiNewsBriefingClient delegate) { this.delegate = delegate; }
    @Override public Draft generate(Source source) { calls.incrementAndGet(); return delegate.generate(source); }
    @Override public String modelName() { return delegate.modelName(); }
    @Override public String promptVersion() { return delegate.promptVersion(); }
  }

  @Configuration(proxyBeanMethods = false) @EnableScheduling
  @Import({GameNewsBriefCommand.BriefingConfig.class, GameNewsSyncConfiguration.class, GameNewsSyncService.class, GameNewsSyncScheduler.class})
  static class VerifyConfig {
    @Bean @Primary CountingAi countingAi(GeminiNewsBriefingClient delegate) { return new CountingAi(delegate); }
  }
}
