-- 공개 부품 가격 이력만 저장. PC/회원/사진 정보 없음.
CREATE TABLE IF NOT EXISTS pc_part_price_snapshots (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 product_code BIGINT NOT NULL,
 price_krw BIGINT NULL,
 status VARCHAR(30) NOT NULL,
 note VARCHAR(300) NOT NULL,
 checked_at VARCHAR(50) NOT NULL,
 INDEX idx_pc_part_price(product_code,id)
);
