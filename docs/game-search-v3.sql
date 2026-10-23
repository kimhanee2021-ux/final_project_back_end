-- MySQL 8+, additive migration. Run before a ddl-auto=validate deployment.
-- Binary collation keeps normalized equality independent of the database's accent/case folding.
CREATE TABLE IF NOT EXISTS game_search_names (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  app_id BIGINT NOT NULL,
  display_name VARCHAR(255) NOT NULL,
  normalized_name VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  kind VARCHAR(16) NOT NULL,
  locale VARCHAR(32) NULL,
  source VARCHAR(100) NOT NULL,
  active BIT NOT NULL,
  CONSTRAINT uk_game_search_name UNIQUE (app_id, normalized_name),
  INDEX idx_game_search_normalized (normalized_name),
  CONSTRAINT fk_game_search_names_game FOREIGN KEY (app_id) REFERENCES games(app_id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS game_news_support (
  app_id BIGINT NOT NULL PRIMARY KEY,
  status VARCHAR(16) NOT NULL,
  source VARCHAR(100) NOT NULL,
  last_checked_at DATETIME(6) NULL,
  CONSTRAINT fk_game_news_support_game FOREIGN KEY (app_id) REFERENCES games(app_id) ON DELETE CASCADE
);
-- Confirmed successful collection is evidence of support. Missing rows remain UNKNOWN.
INSERT IGNORE INTO game_news_support (app_id, status, source, last_checked_at)
SELECT app_id, 'SUPPORTED', 'collection-success', last_success_at
FROM game_news_sync_state WHERE last_success_at IS NOT NULL;
