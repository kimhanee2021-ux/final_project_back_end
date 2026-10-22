package org.ict.final_project_back_end.domain.game.seed;

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ict.final_project_back_end.domain.game.repository.GameRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/*
 * resources/init_data/games_entity.csv 시드 데이터를 games 테이블에 적재하는 임시 러너.
 * - app.seed.games.enabled=true 일 때만 동작 (기본 false, 운영/평소 기동 시 비활성)
 * - games 테이블에 이미 데이터가 있으면 자동으로 스킵 (중복 적재 방지)
 * - CSV를 한 줄씩 스트리밍하면서 chunk-size 단위로 끊고, 청크를 여러 writer 스레드가
 *   병렬로 insert 한다. 청크 하나가 트랜잭션 하나(커밋 1회)라서 행마다 커밋하지 않는다.
 * - 대기 큐 크기를 제한해 파싱이 DB보다 앞서 나가도 메모리에 청크가 쌓이지 않는다.
 * - 배치가 다중 행 INSERT 로 합쳐지려면 DB_URL 에 rewriteBatchedStatements=true 가 필요하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GameCsvSeedRunner implements ApplicationRunner {

  private static final String CSV_PATH = "init_data/games_entity.csv";

  private static final String INSERT_SQL = """
      INSERT IGNORE INTO games
        (app_id, name, genre, positive_rate, thumbnail_url, original_price, tags, categories, recommendations, short_description, about_the_game, release_date, last_synced_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
      """;

  private final JdbcTemplate jdbcTemplate;
  private final GameRepository gameRepository;
  private final TransactionTemplate transactionTemplate;

  @Value("${app.seed.games.enabled:false}")
  private boolean seedEnabled;

  @Value("${app.seed.games.chunk-size:2000}")
  private int chunkSize;

  @Value("${app.seed.games.writer-threads:4}")
  private int writerThreads;

  @Override
  public void run(ApplicationArguments args) throws Exception {
    if (!seedEnabled) {
      return;
    }
    if (gameRepository.count() > 0) {
      log.info("games 테이블에 데이터가 이미 존재하여 CSV 시드를 건너뜁니다.");
      return;
    }

    long started = System.currentTimeMillis();
    AtomicLong inserted = new AtomicLong();
    long parsed = 0;
    long skipped = 0;
    long duplicated = 0;
    // 이름이 완전히 같은 행은 CSV에서 먼저 나온 것만 남긴다. 청크로 나누기 전 파싱 스레드 하나에서만 걸러서 writer 스레드 간 경합이 없다.
    Set<String> seenNames = new HashSet<>();

    // 큐가 가득 차면 파싱 스레드(호출자)가 직접 실행해서 자연스럽게 속도를 맞춘다.
    ExecutorService executor = new ThreadPoolExecutor(
        writerThreads, writerThreads, 0L, TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(writerThreads * 2),
        new ThreadPoolExecutor.CallerRunsPolicy());
    List<Future<?>> futures = new ArrayList<>();

    // 본문에 백슬래시가 섞여 있어도 따옴표 처리가 어긋나지 않도록 escape 문자를 끈다(RFC4180 방식).
    try (CSVReader reader = new CSVReaderBuilder(
        new InputStreamReader(new ClassPathResource(CSV_PATH).getInputStream(), StandardCharsets.UTF_8))
        .withCSVParser(new CSVParserBuilder().withEscapeChar(CSVParser.NULL_CHARACTER).build())
        .build()) {

      // 컬럼 순서가 바뀌어도 깨지지 않도록 헤더 이름으로 인덱스를 찾는다.
      Map<String, Integer> col = new HashMap<>();
      String[] header = reader.readNext();
      for (int i = 0; i < header.length; i++) {
        col.put(header[i].replace("﻿", "").trim(), i);
      }

      List<Object[]> chunk = new ArrayList<>(chunkSize);
      String[] row;
      while ((row = reader.readNext()) != null) {
        if (row.length != header.length) {
          skipped++; // 깨진 행은 건너뜀
          continue;
        }
        String name = row[col.get("Name")];
        // 대소문자·공백까지 그대로 같은 이름만 중복으로 본다. 이름이 비어 있는 행은 서로 같은 게임이 아니므로 거르지 않는다.
        if (name != null && !name.isEmpty() && !seenNames.add(name)) {
          duplicated++;
          continue;
        }
        chunk.add(toParams(row, col));
        parsed++;
        if (chunk.size() >= chunkSize) {
          futures.add(submit(executor, chunk, inserted));
          chunk = new ArrayList<>(chunkSize);
        }
      }
      if (!chunk.isEmpty()) {
        futures.add(submit(executor, chunk, inserted));
      }

      for (Future<?> future : futures) {
        future.get(); // writer 에서 난 예외를 여기서 다시 던진다.
      }
    } finally {
      executor.shutdown();
    }

    log.info("게임 CSV 시드 완료: 파싱 {}건, 삽입 {}건, 이름 중복 제외 {}건, 건너뜀 {}건, {}ms",
        parsed, inserted.get(), duplicated, skipped, System.currentTimeMillis() - started);
  }

  private Future<?> submit(ExecutorService executor, List<Object[]> chunk, AtomicLong inserted) {
    return executor.submit(() -> {
      // 청크 전체를 한 트랜잭션으로 묶어 커밋 횟수를 줄인다.
      transactionTemplate.executeWithoutResult(status -> {
        int[][] results = jdbcTemplate.batchUpdate(INSERT_SQL, chunk, chunk.size(),
            (ps, p) -> {
              for (int i = 0; i < p.length; i++) {
                ps.setObject(i + 1, p[i]);
              }
            });
        long count = 0;
        for (int[] result : results) {
          for (int r : result) {
            // rewriteBatchedStatements 사용 시 SUCCESS_NO_INFO(-2)가 올 수 있어 성공으로 센다.
            count += (r == Statement.SUCCESS_NO_INFO) ? 1 : Math.max(r, 0);
          }
        }
        inserted.addAndGet(count);
      });
    });
  }

  private Object[] toParams(String[] row, Map<String, Integer> col) {
    return new Object[]{
        Long.parseLong(row[col.get("AppID")].trim()),
        row[col.get("Name")],
        nullIfBlank(row[col.get("Genres")]),
        parseBigDecimalOrNull(row[col.get("Positive ratio")]),
        nullIfBlank(row[col.get("Header image")]),
        parseBigDecimalOrNull(row[col.get("Original price KRW")]),
        nullIfBlank(row[col.get("Tags")]),
        nullIfBlank(row[col.get("Categories")]),
        parseIntOrNull(row[col.get("Recommendations")]),
        nullIfBlank(row[col.get("Short description")]),
        nullIfBlank(row[col.get("About the game")]),
        parseDateOrNull(row[col.get("Release date")]),
    };
  }

  private LocalDate parseDateOrNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return LocalDate.parse(value.trim());
    } catch (DateTimeParseException e) {
      return null; // 형식이 다른 값은 결측으로 처리
    }
  }

  private String nullIfBlank(String value) {
    return (value == null || value.isBlank()) ? null : value;
  }

  private Integer parseIntOrNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    // 소수점 표기("0.0")로 들어오는 경우까지 방어적으로 처리
    return (int) Double.parseDouble(value.trim());
  }

  private BigDecimal parseBigDecimalOrNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return new BigDecimal(value.trim());
  }
}
