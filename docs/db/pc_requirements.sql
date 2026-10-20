-- PC 개인정보/이미지 저장 없음. 공식 게임 요구 사양 수집 이력만 보관한다.
CREATE TABLE IF NOT EXISTS pc_requirement_snapshots (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 app_id BIGINT NOT NULL,
 name VARCHAR(200) NOT NULL,
 minimum_text MEDIUMTEXT NOT NULL,
 recommended_text MEDIUMTEXT NOT NULL,
 windows_supported BIT(1) NOT NULL,
 checked_at VARCHAR(50) NOT NULL,
 source_url VARCHAR(300) NULL,
 INDEX idx_pc_req_app_checked (app_id,checked_at)
) DEFAULT CHARSET=utf8mb4;
