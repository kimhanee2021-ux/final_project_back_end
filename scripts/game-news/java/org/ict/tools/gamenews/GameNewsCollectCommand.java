package org.ict.tools.gamenews;

import io.github.cdimascio.dotenv.Dotenv;
import org.ict.final_project_back_end.domain.game.entity.Game;
import org.ict.final_project_back_end.domain.game.repository.GameRepository;
import org.ict.final_project_back_end.domain.gamenews.client.SteamNewsClient;
import org.ict.final_project_back_end.domain.gamenews.entity.GameNews;
import org.ict.final_project_back_end.domain.gamenews.repository.GameNewsRepository;
import org.ict.final_project_back_end.domain.gamenews.service.*;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.*;

/** Isolated CLI: no web server, component scan, AI, application runners, scheduler or schema updates. */
public class GameNewsCollectCommand {
  public static void main(String[] args) throws Exception {
    // The main runtime includes devtools; its restart launcher would replace these CLI arguments.
    System.setProperty("spring.devtools.restart.enabled", "false");
    if (args.length != 2) throw new IllegalArgumentException("Specify app IDs and per-game news count");
    List<Long> ids = Arrays.stream(args[0].split(",", -1)).map(String::strip).map(Long::parseLong).distinct().toList();
    int count = Integer.parseInt(args[1]);
    if (ids.isEmpty() || ids.size() > 20) throw new IllegalArgumentException("Specify 1-20 games");
    ids.forEach(id -> SteamNewsClient.validateRequest(id, count));

    var dotenv = Dotenv.configure().ignoreIfMissing().load();
    for (String key : List.of("DB_URL", "DB_USERNAME", "DB_PASSWORD")) {
      String value = dotenv.get(key);
      if (value == null) throw new IllegalArgumentException("Missing connection setting: " + key);
      System.setProperty(key, value);
    }
    try (var context = new SpringApplicationBuilder(CollectorConfig.class)
        .web(WebApplicationType.NONE)
        .properties(Map.of("spring.datasource.url", "${DB_URL}",
            "spring.datasource.username", "${DB_USERNAME}", "spring.datasource.password", "${DB_PASSWORD}",
            "spring.datasource.driver-class-name", "com.mysql.cj.jdbc.Driver"))
        .run("--spring.config.name=game-news-collector", "--spring.jpa.hibernate.ddl-auto=validate",
            "--spring.jpa.show-sql=false", "--spring.jpa.open-in-view=false",
            "--spring.main.banner-mode=off", "--logging.level.root=WARN")) {
      var games = context.getBean(GameRepository.class);
      // Validate the entire requested set before writing the first game.
      for (long id : ids) {
        if (!games.existsById(id)) throw new IllegalArgumentException("Game not found in games: " + id);
      }
      var collector = context.getBean(GameNewsCollectionService.class);
      int failures = 0;
      for (int index = 0; index < ids.size(); index++) {
        if (index > 0) Thread.sleep(1000);
        long id = ids.get(index);
        try {
          var result = collector.collect(id, count);
          System.out.printf("app_id=%d received=%d inserted=%d updated=%d unchanged=%d stale=%s%n",
              id, result.received(), result.inserted(), result.updated(), result.unchanged(), result.stale());
        } catch (RuntimeException failure) {
          failures++;
          System.err.printf("app_id=%d FAILED (%s); check game_news_sync_state.last_error%n",
              id, failure.getClass().getSimpleName());
        }
        if (Thread.currentThread().isInterrupted()) break;
      }
      if (failures > 0) throw new IllegalStateException("News collection failed for " + failures + " game(s)");
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  @EntityScan(basePackageClasses = {Game.class, GameNews.class})
  @EnableJpaRepositories(basePackageClasses = {GameRepository.class, GameNewsRepository.class})
  @Import({SteamNewsClient.class, GameNewsWriter.class, GameNewsCollectionService.class})
  static class CollectorConfig { }
}
