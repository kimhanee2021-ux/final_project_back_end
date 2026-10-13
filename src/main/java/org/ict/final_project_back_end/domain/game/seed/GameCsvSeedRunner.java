package org.ict.final_project_back_end.domain.game.seed;

import com.opencsv.CSVReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ict.final_project_back_end.domain.game.repository.GameRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/*
 * resources/init_data/games_entity.csv 시드 데이터를 games 테이블에 적재하는 임시 러너.
 * - app.seed.games.enabled=true 일 때만 동작 (기본 false, 운영/평소 기동 시 비활성)
 * - games 테이블에 이미 데이터가 있으면 자동으로 스킵 (중복 적재 방지)
 * - CSV를 전부 메모리에 올리지 않고 한 줄씩 스트리밍하면서 chunk-size 단위로 끊어
 *   JdbcTemplate 배치 insert를 실행한다. 청크마다 별도 커밋이 일어나므로
 *   12만 건 이상을 한 트랜잭션에 몰아넣지 않고 DB 부담을 분산시킨다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GameCsvSeedRunner implements ApplicationRunner {

  private static final String CSV_PATH = "init_data/games_entity.csv";

  private static final String INSERT_SQL = """
      INSERT IGNORE INTO games
        (app_id, name, genre, price_info, discount_percent, review_count, positive_rate, thumbnail_url, description, last_synced_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
      """;

  private final JdbcTemplate jdbcTemplate;
  private final GameRepository gameRepository;

  @Value("${app.seed.games.enabled:false}")
  private boolean seedEnabled;

  @Value("${app.seed.games.chunk-size:500}")
  private int chunkSize;

  @Override
  public void run(ApplicationArguments args) throws Exception {
    if (!seedEnabled) {
      return;
    }
    if (gameRepository.count() > 0) {
      log.info("games 테이블에 데이터가 이미 존재하여 CSV 시드를 건너뜁니다.");
      return;
    }

    long total = 0;
    try (CSVReader reader = new CSVReader(
        new InputStreamReader(new ClassPathResource(CSV_PATH).getInputStream(), StandardCharsets.UTF_8))) {

      reader.readNext(); // 헤더 라인 스킵

      List<Object[]> batch = new ArrayList<>(chunkSize);
      String[] row;
      while ((row = reader.readNext()) != null) {
        batch.add(toParams(row));
        if (batch.size() >= chunkSize) {
          total += flush(batch);
        }
      }
      total += flush(batch);
    }

    log.info("게임 CSV 시드 완료: 총 {}건 삽입 시도", total);
  }

  private int flush(List<Object[]> batch) {
    if (batch.isEmpty()) {
      return 0;
    }
    int size = batch.size();
    jdbcTemplate.batchUpdate(INSERT_SQL, batch);
    batch.clear();
    return size;
  }

  private Object[] toParams(String[] row) {
    return new Object[]{
        Long.parseLong(row[0].trim()),
        row[1],
        nullIfBlank(row[2]),
        nullIfBlank(row[3]),
        parseIntOrNull(row[4]),
        parseIntOrNull(row[5]),
        parseBigDecimalOrNull(row[6]),
        nullIfBlank(row[7]),
        nullIfBlank(row[8]),
    };
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
