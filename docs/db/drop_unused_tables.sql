-- 2026-10-20 사용하지 않는 엔티티를 코드에서 지웠으므로, 로컬 DB에 남은 빈 테이블도 정리한다.
-- 확인한 내용: 아래 23개 테이블은 모두 행이 0개이고, 이 목록 밖의 테이블에서 오는 외래키 참조가 없다.
-- MySQL Workbench 등에서 final_project_db를 선택한 뒤 실행한다.
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS
  beta_feedback, beta_test_details, test_participations,
  ai_chat_messages, ai_chat_message_intents, ai_chat_message_scores, ai_chat_sessions,
  daily_fortune_draws, room_placements, shop_items, taste_choices, user_items,
  news_articles, reports, user_flags, external_reviews, reviews,
  game_dropouts, purchase_emotions, purchase_plans, user_community_stats,
  game_requirements_cache, user_pc_specs;
SET FOREIGN_KEY_CHECKS = 1;
