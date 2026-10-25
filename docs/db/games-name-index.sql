-- AI 게임 검색에서 사용자가 말한 게임 이름으로 games를 찾을 때 쓰는 인덱스 (MySQL 8).
-- 인덱스가 없으면 이름 조회 한 번에 13만 행을 다 읽어 약 3초가 걸린다 (2026-10 측정). 데이터는 바꾸지 않는다.
-- 서버 시작 시 자동 실행되지 않는다 (ddl-auto=none). 인덱스가 없는 DB에서 한 번 직접 실행한다.
CREATE INDEX idx_games_name ON games (name);
