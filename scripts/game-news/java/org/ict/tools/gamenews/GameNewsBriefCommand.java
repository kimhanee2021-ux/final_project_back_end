package org.ict.tools.gamenews;

import io.github.cdimascio.dotenv.Dotenv;
import org.ict.final_project_back_end.domain.aicoach.client.GeminiCoachClient;
import org.ict.final_project_back_end.domain.game.repository.GameRepository;
import org.ict.final_project_back_end.domain.gamenews.client.*;
import org.ict.final_project_back_end.domain.gamenews.dto.NewsBriefingResult.Outcome;
import org.ict.final_project_back_end.domain.gamenews.service.*;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.*;

/** Explicit CLI only: stored-news briefing, or collection followed by briefing. */
public class GameNewsBriefCommand {
  public static void main(String[] args) throws Exception {
    System.setProperty("spring.devtools.restart.enabled", "false");
    if (args.length != 3 || !Set.of("brief", "sync").contains(args[0])) throw new IllegalArgumentException("Expected brief/sync, app IDs, count");
    boolean sync = args[0].equals("sync");
    List<Long> ids = Arrays.stream(args[1].split(",", -1)).map(String::strip).map(Long::parseLong).distinct().toList();
    int count = Integer.parseInt(args[2]);
    if (ids.isEmpty() || ids.size() > 20) throw new IllegalArgumentException("Specify 1-20 games");
    ids.forEach(id -> SteamNewsClient.validateRequest(id, count));
    var dotenv = Dotenv.configure().ignoreIfMissing().load();
    for (String key : List.of("DB_URL", "DB_USERNAME", "DB_PASSWORD", "GEMINI_API_KEY", "GEMINI_COACH_MODEL", "GEMINI_BASE_URL")) {
      String value = dotenv.get(key);
      if (value != null) System.setProperty(key, value);
    }
    if (System.getProperty("GEMINI_API_KEY", "").isBlank()) throw new IllegalArgumentException("GEMINI_API_KEY is required");
    try (var context = new SpringApplicationBuilder(BriefingConfig.class).web(WebApplicationType.NONE)
        .properties(Map.of("spring.datasource.url", "${DB_URL}", "spring.datasource.username", "${DB_USERNAME}",
            "spring.datasource.password", "${DB_PASSWORD}", "spring.datasource.driver-class-name", "com.mysql.cj.jdbc.Driver",
            "app.coach.api-key", "${GEMINI_API_KEY}", "app.coach.model", "${GEMINI_COACH_MODEL:gemini-3.5-flash-lite}",
            "app.coach.base-url", "${GEMINI_BASE_URL:https://generativelanguage.googleapis.com}"))
        .run("--spring.config.name=game-news-briefing", "--spring.jpa.hibernate.ddl-auto=validate",
            "--spring.jpa.show-sql=false", "--spring.jpa.open-in-view=false", "--spring.main.banner-mode=off", "--logging.level.root=WARN")) {
      var games = context.getBean(GameRepository.class);
      for (long id : ids) if (!games.existsById(id)) throw new IllegalArgumentException("Game not found in games: " + id);
      var collector = context.getBean(GameNewsCollectionService.class);
      var briefing = context.getBean(GameNewsBriefingService.class);
      int unfinished = 0;
      for (long id : ids) {
        if (sync) {
          var collected = collector.collect(id, count);
          System.out.printf("app_id=%d inserted=%d updated=%d unchanged=%d%n", id, collected.inserted(), collected.updated(), collected.unchanged());
        }
        var outcomes = new EnumMap<Outcome, Integer>(Outcome.class);
        for (long newsId : briefing.latestNewsIds(id, count)) {
          var result = briefing.generate(newsId);
          outcomes.merge(result.outcome(), 1, Integer::sum);
          System.out.printf("app_id=%d news_id=%d briefing=%s%n", id, newsId, result.outcome());
          if (result.outcome() != Outcome.GENERATED && result.outcome() != Outcome.UP_TO_DATE) unfinished++;
          if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Briefing interrupted");
          if (result.outcome() == Outcome.GENERATED) Thread.sleep(1000);
        }
        System.out.printf("app_id=%d results=%s%n", id, outcomes);
      }
      if (unfinished > 0) throw new IllegalStateException("Unfinished briefings: " + unfinished + "; inspect game_news_briefings status/retry time");
    }
  }

  @Configuration(proxyBeanMethods = false)
  @Import({GameNewsCollectCommand.CollectorConfig.class, GeminiCoachClient.class, GeminiNewsBriefingClient.class,
      GameNewsBriefingWriter.class, GameNewsBriefingService.class})
  static class BriefingConfig { }
}
