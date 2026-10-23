-- Verified against the existing MySQL 8.0.46 schema.
-- One-time migration. DDL implicitly commits; do not run again after application.
-- game_rank now means display position of original products, including hardware.
ALTER TABLE rank_weekly_entries
  DROP CHECK chk_game_rank,
  ADD CONSTRAINT chk_game_rank CHECK (game_rank BETWEEN 1 AND 10),
  ADD COLUMN image_url_snapshot TEXT NULL,
  ADD COLUMN rank_change_kind VARCHAR(16) NULL;
