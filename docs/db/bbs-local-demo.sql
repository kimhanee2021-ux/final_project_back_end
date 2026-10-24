-- BBS 검색/뉴스/음성 입력만 빈 로컬 DB에서 확인하는 수동 준비 파일.
-- MySQL에서 먼저 목적 DB를 선택한다. 전체 앱/사용자/로그인 스키마가 아니다.
-- 기존 행을 수정/삭제하지 않으며 서버 시작 시 자동 실행하지 않는다.
CREATE TABLE IF NOT EXISTS games (
  app_id BIGINT NOT NULL PRIMARY KEY,
  description TEXT NULL,
  discount_percent TINYINT NULL,
  genre VARCHAR(255) NULL,
  last_synced_at DATETIME(6) NULL,
  min_spec_raw_text TEXT NULL,
  name VARCHAR(255) NOT NULL,
  positive_rate DECIMAL(5,2) NULL,
  price_info VARCHAR(100) NULL,
  rec_spec_raw_text TEXT NULL,
  review_count INT NULL,
  thumbnail_url VARCHAR(500) NULL,
  about_the_game TEXT NULL,
  categories TEXT NULL,
  original_price DECIMAL(10,2) NULL,
  recommendations INT NULL,
  release_date DATE NULL,
  short_description TEXT NULL,
  tags TEXT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS game_search_names (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  app_id BIGINT NOT NULL,
  display_name VARCHAR(255) NOT NULL,
  normalized_name VARCHAR(255) NOT NULL,
  compact_name VARCHAR(255) NULL,
  kind VARCHAR(16) NOT NULL,
  locale VARCHAR(32) NULL,
  source VARCHAR(100) NOT NULL,
  active BIT NOT NULL,
  CONSTRAINT uk_game_search_name UNIQUE (app_id, normalized_name),
  CONSTRAINT fk_bbs_search_game FOREIGN KEY (app_id) REFERENCES games(app_id),
  INDEX idx_game_search_normalized (normalized_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO games (app_id, name) SELECT 730, 'Counter-Strike 2' WHERE NOT EXISTS (SELECT 1 FROM games WHERE app_id = 730);
INSERT INTO games (app_id, name) SELECT 570, 'Dota 2' WHERE NOT EXISTS (SELECT 1 FROM games WHERE app_id = 570);
INSERT INTO games (app_id, name) SELECT 413150, 'Stardew Valley' WHERE NOT EXISTS (SELECT 1 FROM games WHERE app_id = 413150);

INSERT INTO game_search_names (app_id, display_name, normalized_name, compact_name, kind, locale, source, active)
SELECT 730, '카운터 스트라이크 2', '카운터 스트라이크 2', '카운터스트라이크2', 'ALIAS', 'ko', 'bbs-local-demo', 1
WHERE NOT EXISTS (SELECT 1 FROM game_search_names WHERE app_id = 730 AND normalized_name = '카운터 스트라이크 2');
INSERT INTO game_search_names (app_id, display_name, normalized_name, compact_name, kind, locale, source, active)
SELECT 730, '카운터스트라이크 2', '카운터스트라이크 2', '카운터스트라이크2', 'ALIAS', 'ko', 'bbs-local-demo', 1
WHERE NOT EXISTS (SELECT 1 FROM game_search_names WHERE app_id = 730 AND normalized_name = '카운터스트라이크 2');
INSERT INTO game_search_names (app_id, display_name, normalized_name, compact_name, kind, locale, source, active)
SELECT 570, '도타 2', '도타 2', '도타2', 'ALIAS', 'ko', 'bbs-local-demo', 1
WHERE NOT EXISTS (SELECT 1 FROM game_search_names WHERE app_id = 570 AND normalized_name = '도타 2');
INSERT INTO game_search_names (app_id, display_name, normalized_name, compact_name, kind, locale, source, active)
SELECT 413150, '스타듀 밸리', '스타듀 밸리', '스타듀밸리', 'ALIAS', 'ko', 'bbs-local-demo', 1
WHERE NOT EXISTS (SELECT 1 FROM game_search_names WHERE app_id = 413150 AND normalized_name = '스타듀 밸리');
