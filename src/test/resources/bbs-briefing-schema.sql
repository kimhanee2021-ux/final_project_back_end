CREATE TABLE IF NOT EXISTS game_news (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, app_id BIGINT NOT NULL, source_news_id VARCHAR(64) NOT NULL,
 title VARCHAR(1000) NOT NULL, content LONGTEXT NOT NULL, content_hash VARCHAR(64) NOT NULL,
 source_url VARCHAR(2048) NOT NULL, feed_name VARCHAR(100) NOT NULL, published_at DATETIME(6),
 collected_at DATETIME(6) NOT NULL, last_seen_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 CONSTRAINT uq_game_news_source UNIQUE(app_id,source_news_id), FOREIGN KEY(app_id) REFERENCES games(app_id)
);
CREATE TABLE IF NOT EXISTS game_news_briefings (
 news_id BIGINT PRIMARY KEY, ai_content TEXT, category VARCHAR(30), generated_at DATETIME(6),
 generated_from_hash VARCHAR(64), generation_status VARCHAR(20) NOT NULL, last_error VARCHAR(1000),
 model_name VARCHAR(100), next_retry_at DATETIME(6), processing_started_at DATETIME(6),
 prompt_version VARCHAR(30), retry_count INT NOT NULL, summary VARCHAR(500),
 gpt_payload LONGTEXT, gpt_source_hash VARCHAR(64), gpt_model_name VARCHAR(100), gpt_prompt_version VARCHAR(30),
 gpt_generated_at DATETIME(6), gpt_claim_token VARCHAR(36), gpt_claim_until DATETIME(6),
 FOREIGN KEY(news_id) REFERENCES game_news(id)
);
