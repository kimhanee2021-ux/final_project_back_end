# Steam 국가별 주간 원본 상품 TOP 10 적재

기존 `rank_weekly_charts` / `rank_weekly_entries` 전용 도구입니다.
기본 실행은 JSON 정제·검증만 수행하며 DB 접속 및 파일 출력이 없습니다.
현재 운영 중인 로컬 DB에는 이미 변경·적재한 이력이 있으므로 아래 적용 명령을 다시 실행할 필요가 없습니다.

## 포함 파일

- `migrate_existing_top10.sql`: 기존 entries 테이블의 `game_rank` CHECK를 1~5에서 1~10으로 확장하고 `image_url_snapshot TEXT NULL`, `rank_change_kind VARCHAR(16) NULL` 추가
- `check_existing_rank_tables.sql`: 기존 테이블 구조 확인용 조회 SQL
- `import_existing_tables.py`: 기존 두 테이블용 적재 진입점
- `normalize_rankings.py`: 순위·상품 ID·기간·출처·상태·중복 검증
- `existing_db.py`: 명시적 적용 시에만 연결 설정 로드
- `data/dataset.json`: 수집 시점의 출처·검증 상태가 포함된 원본 아카이브
- `requirements.txt`: 적용 시 필요한 PyMySQL 및 MySQL 인증 의존성

순위 기준은 판매 개수가 아닌 Steam 매출(revenue) 기준입니다.
원본 상품 순위를 유지하므로 하드웨어도 포함될 수 있습니다.
이 데이터는 최신 자동 수집 결과가 아닙니다. JSON에 기록된 국가·주차·출처·수집 시점을 기준으로 사용하세요.
19개국 114개 차트 상태, 상품 순위 600행을 포함합니다.
미제공·미수집 상태의 차트에는 순위 행을 만들지 않습니다.

## 1. DB 접속 없이 검증 (권장)

Python 3.10 이상을 사용합니다. PowerShell에서 백엔드 저장소 루트로 이동한 뒤 실행합니다.

```powershell
python -X utf8 scripts/weekly-rankings/import_existing_tables.py
```

예상 결과: `{"mode": "OFFLINE", "charts": 114, "entries": 600}`
이 단계에는 패키지 설치나 `.env`가 필요하지 않습니다.
다른 JSON을 검증하려면 `--input "파일 경로"`를 지정합니다.

## 2. 향후 별도 DB에 실제 적용할 때만

아래는 실행 방법 참고용이며, 이번 파일 정리 작업에서는 실행하지 않았습니다.
대상 DB에 기존 두 테이블이 먼저 있어야 합니다. 신규 DB용 CREATE TABLE 스크립트는 아닙니다.

```powershell
python -m venv scripts/weekly-rankings/.venv
scripts/weekly-rankings/.venv/Scripts/python.exe -m pip install -r scripts/weekly-rankings/requirements.txt
```

백엔드 루트 `.env`에 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`를 설정합니다.
`DB_URL` 형식은 `jdbc:mysql://호스트:3306/DB이름`입니다.
동일 이름의 환경변수가 있으면 `.env`보다 우선합니다.
다른 설정 파일은 환경변수 `RANK_BACKEND_ENV`로 지정합니다.
실제 접속 정보는 Git에 넣지 마세요.

기존 TOP 5 스키마에 **처음** 변경과 적재를 함께 적용:

```powershell
scripts/weekly-rankings/.venv/Scripts/python.exe scripts/weekly-rankings/import_existing_tables.py --apply --migrate
```

이미 TOP 10 스키마인 DB에 데이터만 적용:

```powershell
scripts/weekly-rankings/.venv/Scripts/python.exe scripts/weekly-rankings/import_existing_tables.py --apply
```

`--migrate`는 일회성입니다. 이미 컬럼이 있거나 CHECK 구조가 예상과 다르면 중단합니다.
SQL을 Workbench에서 별도로 실행했다면 `--migrate`를 사용하지 마세요.
MySQL DDL은 자동 커밋되므로 이후 데이터 적재 실패가 테이블 변경까지 되돌리지는 않습니다.
`rank_weekly_charts`의 컬럼 구조는 변경하지 않습니다.

## 적재 동작과 보존

- 적용 전 두 테이블의 DDL과 전체 행을 `db-backups/`에 저장합니다.
- 동일 국가·주차 데이터가 완전히 일치하면 건너뜁니다.
- 기존 데이터와 차이가 있으면 덮어쓰지 않고 데이터 트랜잭션을 롤백합니다.
- 차트별 TOP 10의 순위와 개수를 검증한 뒤 커밋합니다.
- 실행 결과는 `existing_import_result.json`에 기록합니다.
- 패키지·번들 상품처럼 app ID가 없는 상품은 현재 테이블에 맞지 않아 적재를 거부합니다.
- 백업, 실행 결과, 가상환경, 자격 증명은 이 폴더의 `.gitignore`로 제외합니다.

## 백엔드 매핑 주의

원본 상품 TOP 10 조회는 `WeeklyProductController`의 JDBC 경로를 사용합니다.
기존 `WeeklyRankEntry` JPA 매핑에는 TOP 5 CHECK가 남아 있으므로
이 패키지로 JPA 스키마 자동 생성이나 기존 TOP 5 경로를 TOP 10으로 전환하지 않습니다.

## 커밋

```powershell
git add scripts/weekly-rankings
git diff --cached --stat
git diff --cached
git commit -m "chore: 주간랭킹 TOP 10 마이그레이션 및 데이터 적재 도구 추가"
```

DB 자체의 변경 사항은 Git에 저장되지 않습니다. SQL·데이터·스크립트를 함께 버전 관리합니다.
