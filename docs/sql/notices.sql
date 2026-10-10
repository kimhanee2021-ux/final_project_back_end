-- 기존 users.id 타입과 부호가 BIGINT와 일치하는지 먼저 확인한다.
-- 이 파일은 테이블 추가용이며 기존 테이블/데이터를 변경하지 않는다.
CREATE TABLE IF NOT EXISTS notices (
  id BIGINT NOT NULL AUTO_INCREMENT,
  title VARCHAR(255) NOT NULL,
  content TEXT NOT NULL,
  author_id BIGINT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  INDEX idx_notices_created_id (created_at, id),
  CONSTRAINT fk_notices_author FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
