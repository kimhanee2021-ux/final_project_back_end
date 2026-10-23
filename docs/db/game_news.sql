-- Steam 공식 소식 저장용 테이블. 기존 games(app_id)를 참조한다.
-- 모든 DATETIME은 UTC로 저장한다. 게임/게시판/기존 news_articles는 변경하지 않는다.
-- 수집 및 AI 호출은 별도 구현이며, 이 스크립트는 데이터를 생성하지 않는다.
-- 기본 상태/카운터/피드명은 Entity에서 설정한다. SQL 직접 입력 시 필수값을 명시한다.
CREATE TABLE IF NOT EXISTS game_news (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  app_id BIGINT NOT NULL,
  source_news_id VARCHAR(64) NOT NULL,
  feed_name VARCHAR(100) NOT NULL,
  title VARCHAR(1000) NOT NULL,
  content LONGTEXT NOT NULL,
  source_url VARCHAR(2048) NOT NULL,
  published_at DATETIME(6) NULL,
  content_hash VARCHAR(64) NOT NULL,
  collected_at DATETIME(6) NOT NULL,
  last_seen_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  CONSTRAINT uq_game_news_source UNIQUE (app_id, source_news_id),
  INDEX idx_game_news_app_published (app_id, published_at, id),
  CONSTRAINT fk_game_news_game FOREIGN KEY (app_id) REFERENCES games(app_id)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS game_news_briefings (
  news_id BIGINT NOT NULL PRIMARY KEY,
  category VARCHAR(30) NULL,
  summary VARCHAR(500) NULL,
  ai_content TEXT NULL,
  generation_status VARCHAR(20) NOT NULL,
  generated_from_hash VARCHAR(64) NULL,
  model_name VARCHAR(100) NULL,
  prompt_version VARCHAR(30) NULL,
  generated_at DATETIME(6) NULL,
  processing_started_at DATETIME(6) NULL,
  retry_count INT NOT NULL,
  next_retry_at DATETIME(6) NULL,
  last_error VARCHAR(1000) NULL,
  INDEX idx_game_news_briefing_retry (generation_status, next_retry_at, news_id),
  INDEX idx_game_news_briefing_category (category, news_id),
  CONSTRAINT fk_game_news_briefing_news FOREIGN KEY (news_id) REFERENCES game_news(id)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS game_news_sync_state (
  app_id BIGINT NOT NULL PRIMARY KEY,
  last_attempt_at DATETIME(6) NULL,
  last_success_at DATETIME(6) NULL,
  next_collect_at DATETIME(6) NOT NULL,
  consecutive_failures INT NOT NULL,
  last_error VARCHAR(1000) NULL,
  INDEX idx_game_news_sync_due (next_collect_at, app_id),
  CONSTRAINT fk_game_news_sync_game FOREIGN KEY (app_id) REFERENCES games(app_id)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
