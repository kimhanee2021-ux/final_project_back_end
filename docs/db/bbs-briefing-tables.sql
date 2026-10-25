-- AI 게임 검색(BBS 통합)의 한국어 요약·낭독에 필요한 테이블 2개를 MySQL 8에 만든다.
-- game_news: 요약한 스팀 공식 글 원문, game_news_briefings: GPT 요약·낭독 대본 (BbsBriefingRepository가 쓴다)
-- 정의는 백엔드 테스트 스키마(src/test/resources/bbs-briefing-schema.sql)와 같고, 이미 있으면 건드리지 않는다.
-- 서버 시작 시 자동 실행되지 않는다 (ddl-auto=none). 테이블이 없는 DB에서 한 번 직접 실행한다.
CREATE TABLE IF NOT EXISTS game_news (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  app_id BIGINT NOT NULL,
  source_news_id VARCHAR(64) NOT NULL,
  title VARCHAR(1000) NOT NULL,
  content LONGTEXT NOT NULL,
  content_hash VARCHAR(64) NOT NULL,
  source_url VARCHAR(2048) NOT NULL,
  feed_name VARCHAR(100) NOT NULL,
  published_at DATETIME(6),
  collected_at DATETIME(6) NOT NULL,
  last_seen_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  CONSTRAINT uq_game_news_source UNIQUE (app_id, source_news_id),
  CONSTRAINT fk_game_news_game FOREIGN KEY (app_id) REFERENCES games(app_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS game_news_briefings (
  news_id BIGINT PRIMARY KEY,
  ai_content TEXT,
  category VARCHAR(30),
  generated_at DATETIME(6),
  generated_from_hash VARCHAR(64),
  generation_status VARCHAR(20) NOT NULL,
  last_error VARCHAR(1000),
  model_name VARCHAR(100),
  next_retry_at DATETIME(6),
  processing_started_at DATETIME(6),
  prompt_version VARCHAR(30),
  retry_count INT NOT NULL,
  summary VARCHAR(500),
  gpt_payload LONGTEXT,
  gpt_source_hash VARCHAR(64),
  gpt_model_name VARCHAR(100),
  gpt_prompt_version VARCHAR(30),
  gpt_generated_at DATETIME(6),
  gpt_claim_token VARCHAR(36),
  gpt_claim_until DATETIME(6),
  CONSTRAINT fk_game_news_briefing_news FOREIGN KEY (news_id) REFERENCES game_news(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
