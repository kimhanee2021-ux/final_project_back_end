-- 읽기 전용 점검. 현재 연결 대상 DB를 선택한 상태에서 실행한다.
-- DDL/DML 없음. games 행 수는 카탈로그 전체를 읽을 수 있으므로 필요한 때만 실행한다.
SELECT TABLE_NAME
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('game', 'games', 'game_news', 'game_news_briefings',
                     'game_news_sync_state', 'game_news_support', 'game_search_names');

SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_KEY
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('game', 'games', 'game_news', 'game_news_briefings',
                     'game_news_sync_state', 'game_news_support', 'game_search_names')
ORDER BY TABLE_NAME, ORDINAL_POSITION;

SELECT TABLE_NAME, INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('games', 'game_news', 'game_news_briefings',
                     'game_news_sync_state', 'game_news_support', 'game_search_names')
ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX;

SELECT TABLE_NAME, COLUMN_NAME, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE() AND REFERENCED_TABLE_NAME IS NOT NULL
  AND TABLE_NAME IN ('game_news', 'game_news_briefings', 'game_news_sync_state',
                     'game_news_support', 'game_search_names')
ORDER BY TABLE_NAME, COLUMN_NAME;

-- 현재 체크아웃의 카탈로그용. game이 정식 테이블인 별도 환경에서는 아래 조회를 실행하지 않는다.
SELECT COUNT(*) AS game_count, MIN(app_id) AS first_app_id, MAX(app_id) AS last_app_id FROM games;
