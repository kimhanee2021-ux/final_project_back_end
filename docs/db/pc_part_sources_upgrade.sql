-- 기존 설치에 한 번 적용. 판매처별 이력을 분리해 서로 다른 가격을 할인율로 오인하지 않는다.
ALTER TABLE pc_part_price_snapshots ADD COLUMN source_key VARCHAR(30) NOT NULL DEFAULT 'DANAWA';
CREATE INDEX idx_pc_part_source ON pc_part_price_snapshots(product_code,source_key,id);
