-- 기존 pc_requirement_snapshots에 source_url이 없을 때 한 번 적용.
-- 새 설치는 pc_requirements.sql에 이미 포함되어 있으므로 실행하지 않는다.
ALTER TABLE pc_requirement_snapshots ADD COLUMN source_url VARCHAR(300) NULL;
