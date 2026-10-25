-- ============================================================
-- GameBalance 축소안 DB 스키마 (MySQL 8.0)
-- 채택된 3개 분류(개인화 / 커뮤니티 / 엔터) 기준으로 재구성
-- 제외: 게임 증권거래소(모의주식), 소셜·친구, 마이데이터·금융, PC기어쇼핑(사양체크는 유지)
-- 균형온도계 / 비슷한 성향 그룹화: 아직 설계 전 (맨 아래 메모 참고)
-- 작성일: 2026-09-22
--
-- [참고용] 실제 테이블은 JPA ddl-auto(update)로 생성한다. 이 파일은 설계 문서이며 실행 대상이 아니다.
--         users / user_owned_games(코드상 library_games) 정의는 실제 엔티티와 다르다.
--
-- [MySQL 8 변환 내역 - 2026-10-07]
--  1. COMMENT ON TABLE (PostgreSQL 문법) → CREATE TABLE ... COMMENT='...' 테이블 옵션으로 변경
--  2. budgets.year_month → budget_month (YEAR_MONTH는 MySQL 예약어)
--  3. 모든 테이블에 ENGINE=InnoDB, utf8mb4 / utf8mb4_0900_ai_ci 지정 (한글·이모지 저장)
--  4. updated_at 컬럼에 ON UPDATE CURRENT_TIMESTAMP 추가
--  5. CHECK 제약은 8.0.16 이상에서 실제로 검사됨 (그 미만 버전은 무시)
-- ============================================================

SET NAMES utf8mb4;

-- ============================================================
-- 도메인 1. 회원 및 로그인
-- ============================================================

CREATE TABLE users (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  email VARCHAR(255) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NULL,
  nickname VARCHAR(255) NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  withdrawn_at DATETIME NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='회원 계정';

CREATE TABLE social_accounts (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  provider VARCHAR(30) NOT NULL,
  provider_user_id VARCHAR(255) NOT NULL,
  access_token TEXT NULL,
  connected_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_social_provider (provider, provider_user_id),
  UNIQUE KEY uq_social_user_provider (user_id, provider)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='구글/카카오/네이버/스팀 연동';

CREATE TABLE password_reset_tokens (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  token VARCHAR(64) NOT NULL UNIQUE,
  expires_at DATETIME NOT NULL,
  used TINYINT(1) DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='비밀번호 재설정 토큰';

CREATE TABLE login_logs (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  logged_in_at DATETIME NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='로그인 이력(관리자 DAU 추이)';

-- ============================================================
-- 도메인 2. 게임 카탈로그·라이브러리
-- ============================================================

CREATE TABLE games (
  app_id BIGINT PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  genre VARCHAR(255) NULL,
  price_info VARCHAR(100) NULL,
  discount_percent TINYINT NULL,
  review_count INT NULL,
  positive_rate DECIMAL(5,2) NULL,
  original_price DECIMAL(10,2) NULL,
  tags TEXT NULL,
  categories TEXT NULL,
  recommendations INT NULL,
  min_spec_raw_text TEXT NULL,
  rec_spec_raw_text TEXT NULL,
  thumbnail_url VARCHAR(500) NULL,
  short_description TEXT NULL,
  about_the_game TEXT NULL,
  last_synced_at DATETIME NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='스팀 게임 카탈로그(1일 1회 캐싱)';

CREATE TABLE user_owned_games (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  app_id BIGINT NOT NULL,
  playtime_minutes INT DEFAULT 0,
  purchase_price INT NULL,
  purchase_date DATE NULL,
  purchase_was_on_sale TINYINT(1) NULL,
  source VARCHAR(20) NULL,
  last_played_at DATETIME NULL,
  last_synced_at DATETIME NULL,
  UNIQUE KEY uq_owned_user_game (user_id, app_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='내 라이브러리(실연동+가상구매), 가성비 계산 소스';

CREATE TABLE game_genres (
  app_id BIGINT NOT NULL,
  genre VARCHAR(50) NOT NULL,
  PRIMARY KEY (app_id, genre)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='장르 다중값(미확정, 개인화 추천 지원용)';

-- ============================================================
-- 도메인 3. 예산·소비 분석 (개인화)
-- ============================================================

CREATE TABLE budgets (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  budget_month CHAR(7) NOT NULL COMMENT 'YYYY-MM (원안 year_month는 예약어라 변경)',
  limit_amount INT NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_budget_user_month (user_id, budget_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='월 예산 설정 - AI 소비패턴';

CREATE TABLE purchase_emotions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_owned_game_id BIGINT NOT NULL UNIQUE,
  emotion VARCHAR(20) NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='구매 전 감정 체크 - 가치판단 도우미';

CREATE TABLE purchase_plans (
  user_owned_game_id BIGINT PRIMARY KEY,
  pledge_type VARCHAR(20) DEFAULT 'NONE',
  planned_playtime_minutes INT NULL,
  planned_exit_date DATE NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='구매 계획(플레이 약속·정리 예정일) - 가치판단 도우미';

CREATE TABLE game_dropouts (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_owned_game_id BIGINT NOT NULL UNIQUE,
  app_id BIGINT NOT NULL,
  playtime_at_drop_minutes INT NOT NULL,
  reason_code VARCHAR(20) NOT NULL,
  reason_text VARCHAR(200) NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_dropout_app (app_id, playtime_at_drop_minutes)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='포기 기록(어디서 왜 그만뒀는지) - 선택이유 모아두기';

-- ============================================================
-- 도메인 4. PC 사양 체크 (개인화 - 최소사양)
-- ============================================================

CREATE TABLE user_pc_specs (
  user_id BIGINT PRIMARY KEY,
  cpu_model VARCHAR(100) NULL,
  gpu_model VARCHAR(100) NULL,
  ram_tier VARCHAR(20) NULL,
  updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='유저 확정 PC 사양';

CREATE TABLE game_requirements_cache (
  app_id BIGINT PRIMARY KEY,
  min_cpu_model VARCHAR(100) NULL,
  min_gpu_model VARCHAR(100) NULL,
  rec_cpu_model VARCHAR(100) NULL,
  rec_gpu_model VARCHAR(100) NULL,
  parsed_at DATETIME NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='게임별 최소/권장 사양 파싱 캐시 - 최소사양 체크';

-- ============================================================
-- 도메인 5. 게시판·커뮤니티·리뷰
-- ============================================================

CREATE TABLE board_posts (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  category VARCHAR(30) NOT NULL,
  app_id BIGINT NULL,
  tag VARCHAR(50) NULL,
  title VARCHAR(255) NOT NULL,
  content TEXT NOT NULL,
  image_url VARCHAR(500) NULL,
  is_anonymous TINYINT(1) DEFAULT 0,
  view_count INT DEFAULT 0,
  author_id BIGINT NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
  expires_at DATETIME NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='게시판 본문(가성비랭킹/세일공유/방치게임인증/구매고민QNA/베타/공지)';

CREATE TABLE board_post_images (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  post_id BIGINT NOT NULL,
  image_url VARCHAR(500) NOT NULL,
  sort_order INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='게시글 다중 이미지';

CREATE TABLE board_comments (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  post_id BIGINT NOT NULL,
  author_id BIGINT NOT NULL,
  parent_comment_id BIGINT NULL,
  content VARCHAR(2000) NOT NULL,
  is_accepted TINYINT(1) DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='댓글/대댓글';

CREATE TABLE content_interactions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  target_type VARCHAR(30) NOT NULL,
  target_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  interaction_type VARCHAR(20) NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_interaction (target_type, target_id, user_id, interaction_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='좋아요/싫어요/북마크(폴리모픽)';

CREATE TABLE reviews (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  app_id BIGINT NOT NULL,
  author_id BIGINT NOT NULL,
  rating TINYINT NOT NULL,
  emotion VARCHAR(20) NULL,
  content VARCHAR(2000) NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_review_game_author (app_id, author_id),
  CONSTRAINT chk_review_rating CHECK (rating BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='게임 리뷰(자체 유저 작성분)';

CREATE TABLE reports (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  target_type VARCHAR(30) NOT NULL,
  target_id BIGINT NOT NULL,
  reason VARCHAR(1000) NOT NULL,
  status VARCHAR(30) DEFAULT 'PENDING',
  reporter_user_id BIGINT NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='범용 신고';

CREATE TABLE user_flags (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  flag_type VARCHAR(30) DEFAULT 'ABUSIVE_LANGUAGE',
  reason VARCHAR(500) NULL,
  report_id BIGINT NULL,
  flagged_by BIGINT NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  released_at DATETIME NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='관리자의 나쁜 언어 사용자 표시 - 비속어 필터링(관리자 기능)';

CREATE TABLE external_reviews (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  app_id BIGINT NOT NULL,
  source VARCHAR(30) NOT NULL,
  external_review_id VARCHAR(64) NOT NULL,
  sentiment VARCHAR(10) NOT NULL,
  excerpt VARCHAR(500) NOT NULL,
  summary_ko VARCHAR(500) NULL,
  helpful_votes INT DEFAULT 0,
  author_playtime_minutes INT NULL,
  source_url VARCHAR(500) NOT NULL,
  language VARCHAR(10) NULL,
  posted_at DATETIME NULL,
  collected_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_external_review (source, external_review_id),
  KEY idx_external_review (app_id, sentiment, helpful_votes)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='외부(스팀) 리뷰 중 공감 최다 호평·악평 - 악평호평리뷰';

CREATE TABLE beta_test_details (
  post_id BIGINT PRIMARY KEY,
  genre VARCHAR(100) NULL,
  stage VARCHAR(30) NULL,
  estimated_minutes INT NULL,
  build_link_url VARCHAR(500) NULL,
  questions_json JSON NULL,
  deadline DATETIME NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='베타테스트 상세(board_posts 1:1 확장)';

CREATE TABLE test_participations (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  post_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  started_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_participation (post_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='베타 참여 기록';

CREATE TABLE beta_feedback (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  participation_id BIGINT NOT NULL UNIQUE,
  rating TINYINT NULL,
  answers_json JSON NULL,
  bug_report VARCHAR(2000) NULL,
  status VARCHAR(20) DEFAULT 'UNDECIDED',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='베타 피드백 - 피드백 서비스';

CREATE TABLE user_community_stats (
  user_id BIGINT PRIMARY KEY,
  activity_points INT DEFAULT 0,
  level INT DEFAULT 1,
  is_promoted TINYINT(1) DEFAULT 0,
  updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='커뮤니티 등업/레벨/포인트';

-- ============================================================
-- 도메인 6. 뉴스 (커뮤니티)
-- ============================================================

CREATE TABLE news_articles (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  title VARCHAR(255) NOT NULL,
  source_name VARCHAR(100) NULL,
  source_url VARCHAR(500) NOT NULL UNIQUE,
  summary VARCHAR(500) NULL,
  thumbnail_url VARCHAR(500) NULL,
  category VARCHAR(20) NULL,
  published_at DATETIME NULL,
  collected_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='게임뉴스(네이버 API HUB 수집)';

-- ============================================================
-- 도메인 7. AI 챗봇 코어 (엔터 - 챗봇+게임의 기반)
-- ============================================================

CREATE TABLE ai_chat_sessions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  title VARCHAR(100) NULL,
  session_type VARCHAR(30) DEFAULT 'COACHING',
  is_private TINYINT(1) DEFAULT 0,
  is_active TINYINT(1) DEFAULT 1,
  last_response_id VARCHAR(100) NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 챗봇 대화 세션(코칭/미니게임 공용)';

CREATE TABLE ai_chat_messages (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  session_id BIGINT NOT NULL,
  sender_type VARCHAR(20) NOT NULL,
  sender_id BIGINT NULL,
  message_text TEXT NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 챗봇 메시지';

CREATE TABLE ai_chat_message_intents (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  message_id BIGINT NOT NULL,
  intent_tag VARCHAR(100) NOT NULL,
  UNIQUE KEY uq_intent (message_id, intent_tag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='메시지 의도 태깅';

CREATE TABLE ai_chat_message_scores (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  message_id BIGINT NOT NULL,
  score_type VARCHAR(50) NOT NULL,
  score_value INT NOT NULL,
  UNIQUE KEY uq_score (message_id, score_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='메시지 점수 태깅';

-- ============================================================
-- 도메인 8. AI 챗봇 컴패니언·미니게임 (엔터 - 챗봇+게임)
-- ============================================================

CREATE TABLE daily_fortune_draws (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  draw_date DATE NOT NULL,
  card_result_json JSON NOT NULL,
  ai_message VARCHAR(500) NULL,
  UNIQUE KEY uq_fortune (user_id, draw_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='운세/타로 뽑기(1일 1회)';

CREATE TABLE shop_items (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  item_name VARCHAR(100) NOT NULL,
  item_type VARCHAR(30) NOT NULL,
  point_cost INT NOT NULL,
  thumbnail_url VARCHAR(500) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='방 꾸미기 상점 카탈로그';

CREATE TABLE user_items (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  item_id BIGINT NOT NULL,
  acquired_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_user_item (user_id, item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='유저 아이템 인벤토리';

CREATE TABLE room_placements (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  user_item_id BIGINT NOT NULL UNIQUE,
  pos_x INT NOT NULL,
  pos_y INT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='방 안 아이템 배치';

-- ============================================================
-- FK 제약조건 일괄 추가
-- ============================================================

ALTER TABLE social_accounts ADD CONSTRAINT fk_social_user FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE password_reset_tokens ADD CONSTRAINT fk_pwreset_user FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE login_logs ADD CONSTRAINT fk_login_user FOREIGN KEY (user_id) REFERENCES users(id);

ALTER TABLE user_owned_games ADD CONSTRAINT fk_owned_user FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE user_owned_games ADD CONSTRAINT fk_owned_game FOREIGN KEY (app_id) REFERENCES games(app_id);
ALTER TABLE game_genres ADD CONSTRAINT fk_genre_game FOREIGN KEY (app_id) REFERENCES games(app_id);

ALTER TABLE budgets ADD CONSTRAINT fk_budget_user FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE purchase_emotions ADD CONSTRAINT fk_emotion_owned FOREIGN KEY (user_owned_game_id) REFERENCES user_owned_games(id);
ALTER TABLE purchase_plans ADD CONSTRAINT fk_plan_owned FOREIGN KEY (user_owned_game_id) REFERENCES user_owned_games(id);
ALTER TABLE game_dropouts ADD CONSTRAINT fk_dropout_owned FOREIGN KEY (user_owned_game_id) REFERENCES user_owned_games(id);
ALTER TABLE game_dropouts ADD CONSTRAINT fk_dropout_game FOREIGN KEY (app_id) REFERENCES games(app_id);

ALTER TABLE user_pc_specs ADD CONSTRAINT fk_pcspec_user FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE game_requirements_cache ADD CONSTRAINT fk_reqcache_game FOREIGN KEY (app_id) REFERENCES games(app_id);

ALTER TABLE board_posts ADD CONSTRAINT fk_post_author FOREIGN KEY (author_id) REFERENCES users(id);
ALTER TABLE board_posts ADD CONSTRAINT fk_post_game FOREIGN KEY (app_id) REFERENCES games(app_id);
ALTER TABLE board_post_images ADD CONSTRAINT fk_postimg_post FOREIGN KEY (post_id) REFERENCES board_posts(id);
ALTER TABLE board_comments ADD CONSTRAINT fk_comment_post FOREIGN KEY (post_id) REFERENCES board_posts(id);
ALTER TABLE board_comments ADD CONSTRAINT fk_comment_author FOREIGN KEY (author_id) REFERENCES users(id);
ALTER TABLE board_comments ADD CONSTRAINT fk_comment_parent FOREIGN KEY (parent_comment_id) REFERENCES board_comments(id);
ALTER TABLE content_interactions ADD CONSTRAINT fk_interaction_user FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE reviews ADD CONSTRAINT fk_review_game FOREIGN KEY (app_id) REFERENCES games(app_id);
ALTER TABLE reviews ADD CONSTRAINT fk_review_author FOREIGN KEY (author_id) REFERENCES users(id);
ALTER TABLE reports ADD CONSTRAINT fk_report_user FOREIGN KEY (reporter_user_id) REFERENCES users(id);
ALTER TABLE user_flags ADD CONSTRAINT fk_flag_user FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE user_flags ADD CONSTRAINT fk_flag_reporter FOREIGN KEY (flagged_by) REFERENCES users(id);
ALTER TABLE user_flags ADD CONSTRAINT fk_flag_report FOREIGN KEY (report_id) REFERENCES reports(id);
ALTER TABLE external_reviews ADD CONSTRAINT fk_extreview_game FOREIGN KEY (app_id) REFERENCES games(app_id);
ALTER TABLE beta_test_details ADD CONSTRAINT fk_beta_post FOREIGN KEY (post_id) REFERENCES board_posts(id);
ALTER TABLE test_participations ADD CONSTRAINT fk_participation_post FOREIGN KEY (post_id) REFERENCES board_posts(id);
ALTER TABLE test_participations ADD CONSTRAINT fk_participation_user FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE beta_feedback ADD CONSTRAINT fk_feedback_participation FOREIGN KEY (participation_id) REFERENCES test_participations(id);
ALTER TABLE user_community_stats ADD CONSTRAINT fk_communitystats_user FOREIGN KEY (user_id) REFERENCES users(id);

ALTER TABLE ai_chat_sessions ADD CONSTRAINT fk_chatsession_user FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE ai_chat_messages ADD CONSTRAINT fk_chatmsg_session FOREIGN KEY (session_id) REFERENCES ai_chat_sessions(id);
ALTER TABLE ai_chat_messages ADD CONSTRAINT fk_chatmsg_sender FOREIGN KEY (sender_id) REFERENCES users(id);
ALTER TABLE ai_chat_message_intents ADD CONSTRAINT fk_intent_msg FOREIGN KEY (message_id) REFERENCES ai_chat_messages(id);
ALTER TABLE ai_chat_message_scores ADD CONSTRAINT fk_score_msg FOREIGN KEY (message_id) REFERENCES ai_chat_messages(id);

ALTER TABLE daily_fortune_draws ADD CONSTRAINT fk_fortune_user FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE user_items ADD CONSTRAINT fk_useritem_user FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE user_items ADD CONSTRAINT fk_useritem_item FOREIGN KEY (item_id) REFERENCES shop_items(id);
ALTER TABLE room_placements ADD CONSTRAINT fk_room_user FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE room_placements ADD CONSTRAINT fk_room_useritem FOREIGN KEY (user_item_id) REFERENCES user_items(id);

-- ============================================================
-- 미정 아이디어 (설계 전 — 테이블 없음)
-- ============================================================
-- 균형온도계: 개인화 분류의 8개 항목 중 하나. 어떤 데이터를 어떻게
-- 보여주는 기능인지 아직 정의되지 않아 테이블 설계 보류.
-- 비슷한 성향 그룹화: f2-2(협업 필터링)와 개념이 겹치나 별도 설계는 아직 없음.
