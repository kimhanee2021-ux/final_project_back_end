package org.ict.tools.gamenews;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cdimascio.dotenv.Dotenv;
import org.ict.final_project_back_end.domain.game.entity.Game;
import org.ict.final_project_back_end.domain.game.repository.GameRepository;
import org.ict.final_project_back_end.domain.gamenews.controller.GameNewsController;
import org.ict.final_project_back_end.domain.gamenews.entity.GameNews;
import org.ict.final_project_back_end.domain.gamenews.entity.GameNewsCategory;
import org.ict.final_project_back_end.domain.gamenews.exception.GameNewsQueryExceptionHandler;
import org.ict.final_project_back_end.domain.gamenews.repository.GameNewsRepository;
import org.ict.final_project_back_end.domain.gamenews.service.GameNewsQueryServiceImpl;
import org.ict.final_project_back_end.global.config.SecurityConfig;
import org.ict.final_project_back_end.global.exception.GlobalExceptionHandler;
import org.ict.final_project_back_end.global.security.JwtAuthFilter;
import org.ict.final_project_back_end.global.security.JwtService;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/** Temporary loopback server: only query components, read-only DB connections, no startup jobs or AI. */
public class GameNewsApiVerifyCommand {
  private static final String PUBLISHED = """
      FROM game_news n JOIN game_news_briefings b ON b.news_id = n.id
      WHERE b.generated_at IS NOT NULL AND b.ai_content IS NOT NULL
        AND b.summary IS NOT NULL AND b.category IS NOT NULL
      """;

  public static void main(String[] args) throws Exception {
    System.setProperty("spring.devtools.restart.enabled", "false");
    if (args.length != 1) throw new IllegalArgumentException("Specify 1-20 existing game app IDs");
    var ids = Arrays.stream(args[0].split(",", -1)).map(String::strip).map(Long::parseLong).distinct().toList();
    if (ids.isEmpty() || ids.size() > 20 || ids.stream().anyMatch(id -> id <= 0 || id > 0xffffffffL)) {
      throw new IllegalArgumentException("Specify 1-20 valid game app IDs");
    }
    var dotenv = Dotenv.configure().ignoreIfMissing().load();
    for (String key : List.of("DB_URL", "DB_USERNAME", "DB_PASSWORD")) {
      String value = dotenv.get(key);
      if (value == null) throw new IllegalArgumentException("Missing connection setting: " + key);
      System.setProperty(key, value);
    }
    try (var context = new SpringApplicationBuilder(QueryConfig.class)
        .properties(Map.of("spring.datasource.url", "${DB_URL}", "spring.datasource.username", "${DB_USERNAME}",
            "spring.datasource.password", "${DB_PASSWORD}", "spring.datasource.driver-class-name", "com.mysql.cj.jdbc.Driver",
            "jwt.secret-key", UUID.randomUUID().toString().repeat(2), "jwt.expiration-ms", "60000",
            "app.frontend-base-url", "http://localhost:5173"))
        .run("--spring.config.name=game-news-api-verification", "--spring.jpa.hibernate.ddl-auto=validate",
            "--spring.datasource.hikari.read-only=true", "--spring.jpa.show-sql=false", "--spring.jpa.open-in-view=false",
            "--spring.main.banner-mode=off", "--logging.level.root=WARN", "--server.address=127.0.0.1", "--server.port=0")) {
      int port = ((WebServerApplicationContext) context).getWebServer().getPort();
      var db = context.getBean(JdbcTemplate.class);
      var before = snapshot(db);
      var checks = new Checks("http://127.0.0.1:" + port);
      var all = checks.get("/api/games/news?size=2", 200);
      require(all.path("totalElements").asLong() == db.queryForObject("SELECT COUNT(*) " + PUBLISHED, Long.class), "Global total");
      for (long appId : ids) {
        String path = "/api/games/" + appId + "/news";
        long count = db.queryForObject("SELECT COUNT(*) " + PUBLISHED + " AND n.app_id = ?", Long.class, appId);
        require(count > 0, "Game must have a saved briefing: " + appId);
        require(count <= 100, "Limit verification to games with at most 100 published briefings");
        var first = checks.get(path + "?size=2", 200);
        require(first.equals(checks.get("/api/games/news?appId=" + appId + "&size=2", 200)), "Both game filters agree");
        require(first.path("totalElements").asLong() == count, "Game total");
        var seen = new HashSet<Long>();
        for (int page = 0; page < first.path("totalPages").asInt(); page++) {
          for (JsonNode item : checks.get(path + "?size=2&page=" + page, 200).path("content")) {
            long id = item.path("newsId").asLong();
            require(seen.add(id) && item.path("appId").asLong() == appId, "Unique paging and game ownership");
            require(!item.has("aiContent") && !item.has("content"), "Compact list DTO");
            var detail = checks.get(path + "/" + id, 200);
            require(detail.path("aiContent").asText().matches("(?s).*[가-힣].*"), "Korean briefing body");
            require(detail.path("generatedAt").asText().endsWith("Z"), "UTC generation time");
            require(!detail.has("lastError") && !detail.has("contentHash") && !detail.has("content"), "No internal data");
            var saved = db.queryForMap("SELECT b.ai_content, b.generated_from_hash = n.content_hash AS current_source "
                + PUBLISHED + " AND n.id = ?", id);
            require(detail.path("aiContent").asText().equals(saved.get("ai_content")), "Stored body matches API");
            require(detail.path("stale").asBoolean() == (((Number) saved.get("current_source")).intValue() == 0), "Source freshness");
          }
        }
        require(seen.size() == count, "Every stored briefing appears exactly once");
        long filtered = 0;
        for (var category : GameNewsCategory.values()) {
          var result = checks.get(path + "?size=100&category=" + category.name(), 200);
          for (JsonNode item : result.path("content")) require(item.path("category").asText().equals(category.name()), "Category filter");
          filtered += result.path("totalElements").asLong();
        }
        require(filtered == count, "Category totals equal game total");
        require(checks.get(path + "/9223372036854775807", 404).path("code").asText().equals("GAME_NEWS_NOT_FOUND"), "Missing detail");
        System.out.printf("app_id=%d list/detail=%d paging=PASS categories=PASS%n", appId, seen.size());
      }
      require(checks.get("/api/games/news?size=101", 400).path("code").asText().equals("INVALID_GAME_NEWS_QUERY"), "Size validation");
      require(checks.get("/api/games/news?category=INVALID", 400).path("code").asText().equals("INVALID_GAME_NEWS_QUERY"), "Category validation");
      require(checks.get("/api/games/news?page=bad", 400).path("code").asText().equals("INVALID_GAME_NEWS_QUERY"), "Type validation");
      require(before.equals(snapshot(db)), "DB snapshot remains unchanged");
      System.out.printf("HTTP checks=%d database=UNCHANGED external_calls=NONE%n", checks.requests);
    }
  }

  private static List<?> snapshot(JdbcTemplate db) {
    return List.of(db.queryForObject("SELECT COUNT(*) FROM games", Long.class),
        db.queryForObject("SELECT COUNT(*) FROM board_posts", Long.class),
        db.queryForList("SELECT * FROM game_news ORDER BY id"),
        db.queryForList("SELECT * FROM game_news_briefings ORDER BY news_id"),
        db.queryForList("SELECT * FROM game_news_sync_state ORDER BY app_id"));
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException("Verification failed: " + message);
  }

  private static class Checks {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json = new ObjectMapper();
    private final String base;
    private int requests;
    Checks(String base) { this.base = base; }
    JsonNode get(String path, int status) throws Exception {
      var response = client.send(HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(20)).GET().build(),
          HttpResponse.BodyHandlers.ofString());
      requests++;
      require(response.statusCode() == status, "HTTP " + status + " " + path + "; actual=" + response.statusCode());
      return json.readTree(response.body());
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  @EntityScan(basePackageClasses = {Game.class, GameNews.class})
  @EnableJpaRepositories(basePackageClasses = {GameRepository.class, GameNewsRepository.class})
  @Import({GameNewsController.class, GameNewsQueryServiceImpl.class, GameNewsQueryExceptionHandler.class,
      GlobalExceptionHandler.class, SecurityConfig.class, JwtAuthFilter.class, JwtService.class})
  static class QueryConfig { }
}
