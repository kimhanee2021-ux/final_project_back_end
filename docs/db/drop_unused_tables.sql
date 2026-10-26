-- 화면·코드에서 쓰지 않는 테이블 정리 (2026-10-20 23개 + 2026-10-26 추가 9개 = 32개).
-- 2026-10-26: 게시판(board_*, content_interactions), 예전 기능(ai_manager_records, game_genres, game_prices_krw,
--   rank_weekly_charts, rank_weekly_entries) 추가. 실행 전 mysqldump로 백업하고, final_project_db를 선택한 뒤 실행한다.
-- 실행 뒤 남는 테이블은 17개다: users, games, library_games, purchase_transactions, purchase_items, game_licenses, budgets,
--   exchange_rates, featured_games, featured_category_games, pc_part_price_snapshots, pc_requirement_snapshots,
--   game_news, game_news_briefings, ai_official_news, ai_game_briefings, ai_briefing_checkpoint
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS `ai_chat_message_intents`;
DROP TABLE IF EXISTS `ai_chat_message_scores`;
DROP TABLE IF EXISTS `ai_chat_messages`;
DROP TABLE IF EXISTS `ai_chat_sessions`;
DROP TABLE IF EXISTS `ai_manager_records`;
DROP TABLE IF EXISTS `beta_feedback`;
DROP TABLE IF EXISTS `beta_test_details`;
DROP TABLE IF EXISTS `board_comments`;
DROP TABLE IF EXISTS `board_post_images`;
DROP TABLE IF EXISTS `board_posts`;
DROP TABLE IF EXISTS `content_interactions`;
DROP TABLE IF EXISTS `daily_fortune_draws`;
DROP TABLE IF EXISTS `external_reviews`;
DROP TABLE IF EXISTS `game_dropouts`;
DROP TABLE IF EXISTS `game_genres`;
DROP TABLE IF EXISTS `game_prices_krw`;
DROP TABLE IF EXISTS `game_requirements_cache`;
DROP TABLE IF EXISTS `news_articles`;
DROP TABLE IF EXISTS `purchase_emotions`;
DROP TABLE IF EXISTS `purchase_plans`;
DROP TABLE IF EXISTS `rank_weekly_charts`;
DROP TABLE IF EXISTS `rank_weekly_entries`;
DROP TABLE IF EXISTS `reports`;
DROP TABLE IF EXISTS `reviews`;
DROP TABLE IF EXISTS `room_placements`;
DROP TABLE IF EXISTS `shop_items`;
DROP TABLE IF EXISTS `taste_choices`;
DROP TABLE IF EXISTS `test_participations`;
DROP TABLE IF EXISTS `user_community_stats`;
DROP TABLE IF EXISTS `user_flags`;
DROP TABLE IF EXISTS `user_items`;
DROP TABLE IF EXISTS `user_pc_specs`;
SET FOREIGN_KEY_CHECKS = 1;
