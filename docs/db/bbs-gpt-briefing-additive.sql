-- BBS04 stage 3-1, MySQL 8. REVIEW BEFORE APPLYING TO A REAL DATABASE.
-- Apply ONCE to a database that already has game_news and game_news_briefings.
-- No startup execution. Keep BBS_BRIEFING_ENABLED=false until reviewed/applied.
-- Inspect the selected database and existing columns before executing ALTER.
SELECT DATABASE() AS target_database;
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'game_news_briefings'
ORDER BY ORDINAL_POSITION;

-- All new columns are nullable. Existing rows, primary/foreign keys,
-- Gemini summary/ai_content/model/status and the games table are preserved.
-- gpt_payload stores the Korean title, exactly 3 points, narration script,
-- and the exact source snapshot used to generate them (JSON encoded text).
ALTER TABLE game_news_briefings
  ADD COLUMN gpt_payload LONGTEXT NULL,
  ADD COLUMN gpt_source_hash VARCHAR(64) NULL,
  ADD COLUMN gpt_model_name VARCHAR(100) NULL,
  ADD COLUMN gpt_prompt_version VARCHAR(30) NULL,
  ADD COLUMN gpt_generated_at DATETIME(6) NULL,
  ADD COLUMN gpt_claim_token VARCHAR(36) NULL,
  ADD COLUMN gpt_claim_until DATETIME(6) NULL;

-- Do not rerun ALTER if these columns already exist; compare their definitions.
-- MySQL DDL commits implicitly. Review a backup and the deployment window first.
