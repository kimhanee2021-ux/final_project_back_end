# GameSense 뉴스·음성 기능 시작 점검

확인일: 2026-10-24 (Asia/Seoul). 이 문서는 현재 체크아웃과 연결된 로컬 DB의 관찰 결과다. 다른 배포 DB에 그대로 적용하지 않는다.

## 현재 작업 기준

| 항목 | 확인 결과 |
|---|---|
| 백엔드 | `kimhanee2021-ux/final_project_back_end`, `feature/ai-spending-coach`, `5f9cb6c` |
| 프론트 | `kimhanee2021-ux/final_project_front_end`, `feature/ai-spending-coach`, `266ca4a` |
| 시작 시 미커밋 변경 | 두 저장소 모두 없음 |
| 기존 뉴스 구현 | 백엔드 `BBS03`의 `9f78937`, 프론트 `BBS03`의 `c59f94e`에 존재. 체크아웃된 소스에는 없음 |
| DB 변경 방식 | Flyway/Liquibase 없음. `docs/db`의 수동 SQL 방식 |

GitHub 연결에서 현재 백엔드 브랜치 접근을 확인했다. Git 이력은 읽기만 했으며 브랜치 전환, merge, cherry-pick, commit, push는 하지 않았다.

## 게임 테이블의 실제 상태

백엔드 `.env`가 가리키는 DB를 JDBC 읽기 전용 트랜잭션으로 조회했다. 접속 정보와 기사·사용자 데이터는 문서에 저장하지 않았다.

- `game` 테이블은 없고 **`games`에 69,919행**이 있다. `app_id`는 `BIGINT` 기본키이며 관찰된 범위는 10~4,437,540이다.
- `games`의 20개 컬럼은 사용자가 전달한 게임 테이블 목록과 일치한다. 현재 `Game`은 이 테이블에 이미 매핑되어 있다.
- DB의 `description`, `min_spec_raw_text`, `rec_spec_raw_text`는 현재 Entity에 없지만 삭제하거나 비우지 않는다. 뉴스 연동에는 `appId`와 `name`만 필요하다.
- `game_news`, `game_news_briefings`, `game_news_sync_state`, `game_search_names`, `news_articles`, `board_posts`가 이미 존재한다. 외래키 조회에서 `game_news_support`도 확인했다.
- `game_news`, `game_news_sync_state`, `game_search_names`, `game_news_support`의 `app_id`는 `games.app_id`를 참조한다.

따라서 `Game` Entity를 새로 만들거나 `@Table`을 `game`으로 바꾸면 안 된다. 단수형 테이블이 있는 별도 팀 DB를 대상으로 할 때는 그 DB의 메타데이터를 먼저 확인한다. `docs/db/drop_unused_tables.sql`은 이 작업에 사용하지 않는다.

이번 변경은 일반 실행 설정의 Hibernate 자동 DDL을 `none`, SQL 초기화를 `never`, 게임 CSV 시드의 기본값을 `false`로 바꾼다. 기존 DB에 대한 자동 스키마 수정·초기 적재를 방지하기 위한 시작 설정이다. 새 DB는 검토한 수동 스키마로 준비해야 한다. 
외부 설정으로 명시적으로 활성화한 시드, 기존 API의 저장 동작, 다른 스케줄러까지 차단하는 설정은 아니다.

## 재사용할 구현과 요청 사이의 차이

`git show <commit>:<path>`로 기존 구현을 읽었으며, 아래 기능의 현재 브랜치 이식은 아직 하지 않았다.

| 구간 | 기존 BBS03 코드 | 유지·보완할 계약 |
|---|---|---|
| 게임 인식 | `domain/game`의 `Game`, `GameRepository`, `GameNameResolver`, `GameSearchName` 및 `GameSearchCatalogSync` | 기존 게임을 참조. 한국어·영어·음성 별칭으로 후보를 찾고 애매한 경우 선택을 요청. AI가 만든 appId를 그대로 신뢰하지 않음 |
| 원문 수집 | `domain/gamenews/client/SteamNewsClient`, `dto/SteamNewsItem`, `GameNewsCollectionService`, `GameNewsWriter` | Steam의 appid를 요청/응답/항목마다 검증. `(app_id, source_news_id)` 중복 방지. gid는 문자열 유지 |
| 저장 | `GameNews`, `GameNewsBriefing`, `GameNewsSyncState`와 Repository | 기존 `Game` 연관관계 재사용. 게임으로 향하는 cascade 없음. 원문 해시 기반 재생성과 실패 상태 재사용 |
| 한국어 AI | `GeminiNewsBriefingClient`, `GameNewsBriefingService`, `GameNewsBriefingWriter` | 기존 원문·출처를 보존하며 한국어 제목·본문·요약·낭독용 대본을 별도로 관리 |
| 조회 | `GameNewsController`, `GameNewsQueryServiceImpl` | 저장된 결과만 반환. 목록 GET에서 Steam 수집·AI 생성·TTS 실행하지 않음 |
| 음성 | `OpenAiNewsSpeechClient`, `NewsSpeechService`, transcription/intent 클라이언트 | 기존 OpenAI 처리 재사용. 게임 식별과 기사 요약과 음성 합성을 분리 |
| 프론트 | `features/game-news`, `pages/game-news`, `/bbs`, `/bbs/:appId/:newsId` | 현재 라우터·메뉴에 맞춰 연결. 일반 `domain/board` 사용자 게시글과 뉴스 데이터를 혼합하지 않음 |

현재 요청을 그대로 만족시키려면 다음 차이를 해결해야 한다.

1. **공식 공지와 전체 뉴스의 범위:** 기존 수집기는 `feeds=steam_community_announcements`를 지정하고 다른 feed를 버린다. 공지 안에는 패치 외 이벤트·개발 소식도 있을 수 있지만, Steam이 제공하는 외부 매체 기사까지 포함하려면 feed 범위를 명시적으로 넓히고 실제 `feed_name` 및 출처를 보존해야 한다. 외부 기사를 공식 공지로 표시하지 않는다.
2. **한국어 제목:** 기존 조회 DTO의 `title`은 원문 제목이다. `summary`/`aiContent`만 한국어이므로 '뉴스를 한글로 전달'하려면 번역 제목을 별도 필드로 추가하고 프론트 표시 정책을 정한다.
3. **낭독 대본:** 기존 TTS는 프론트가 보낸 일반 `text`를 읽는다. 서버가 `newsId`·원문 해시·생성 버전을 기준으로 확인한 한국어 대본을 읽도록 확장하면 잘못된 게임/오래된 요약의 음성 재사용을 막기 쉽다. 게임명·게시일·핵심 변화·출처를 포함하고 발음용 이름을 카탈로그 원본 이름과 분리한다.
4. **6만여 게임의 수집:** 기존 정기 수집 설정은 기본 570·730·440, 최대 20개 appId다. 전체 게임 지원은 전체 카탈로그 매번 호출과 다르다. 보유/관심/최근 요청 게임을 우선 수집하고 나머지는 페이지 단위로 순환한다. `GameRepository.findAppIdsAfter`와 기존 수집 상태/재시도/선점 기능을 재사용한다.
5. **기존 DB와 SQL 검증:** `CREATE TABLE IF NOT EXISTS`는 기존 컬럼·인덱스의 동일성을 검증하지 않는다. 실제 뉴스 테이블의 컬럼·제약조건을 비교한 후 필요한 추가 변경만 별도 마이그레이션으로 준비한다. 기존 테이블 재생성이나 게임 재적재는 하지 않는다.

## 통합 순서와 검증 기준

1. 현재 `Game`/Repository를 유지하고 BBS03 저장 계층과 현재 DB의 컬럼·인덱스·외래키를 대조한다. 뉴스 변경에 필요한 추가 스키마만 준비한다.
2. 기존 수집기에서 feed 범위와 출처 계약을 확장한다. 잘못된 appid, 큰 gid, 중복 기사, 원문 수정, 빈 뉴스, Steam 오류를 격리된 테스트로 검증한다.
3. 한국어 제목·요약·본문·대본을 별도 산출물로 생성하고 모델/프롬프트 버전 및 원문 해시를 기록한다. 실패·재시도·이전 성공본 노출 계약을 유지한다.
4. 조회 API를 현재 예외 체계에 연결하고 BBS 화면의 목록·필터·출처·한국어 제목을 복원한다. 일반 게시판 API는 유지한다.
5. 기존 게임 인식과 TTS를 연결한다. 모호한 게임명, 생성 전 기사, 기사 수정 뒤 대본/오디오 무효화, 재생 취소·실패를 검증한다. 현재 `/api/games/**` 공개 규칙을 고려해 비용이 발생하는 음성 POST의 인증·호출 제한을 함께 검토한다.
6. 테스트는 H2 메모리 DB와 외부 API mock을 우선한다. 실제 DB의 DDL/DML 및 유료 AI 호출은 이 시작 점검에 포함하지 않는다. 브랜치 전환·push·merge는 사용자 확인 후에만 진행한다.

## Steam API 근거

[Valve ISteamNews 공식 문서](https://partner.steamgames.com/doc/webapi/ISteamNews)의 `GET /ISteamNews/GetNewsForApp/v2/`는 `appid`, `maxlength`, `enddate`, `count`, `feeds`를 제공한다. `maxlength=0`은 전체 본문이며 `feeds`는 뉴스 피드 필터다. 문서에는 한국어 번역을 보장하는 파라미터가 없다. 한국어 결과는 별도 번역·요약 단계에서 검증해야 한다. 이 API를 Steam News Hub 화면의 모든 콘텐츠와 동등하다고 가정하지 않는다.


## 이번 시작 작업의 검증 결과

- 백엔드: 게임·게시판·애플리케이션 기동 관련 **11개 테스트 클래스, 63개 테스트 통과**, 실패·오류·건너뜀 0.
- 테스트 연결은 `jdbc:h2:mem:news-start-tests`로 명시했다. 테스트에서만 사용하는 `create-drop`은 H2 메모리에만 적용했다. MySQL 연결이나 앱의 main 실행 없이 자동 수집·시드·가격 동기화를 비활성화했다.
- 프론트: `npm run build` 성공. 500 kB 초과 번들 경고가 있으나 이번 작업에서 프론트 소스는 변경하지 않았다.
- 일반 실행용 YAML 파싱 및 DDL/SQL 초기화/시드 기본값 확인 완료. 점검 SQL은 SELECT 5개뿐임을 확인했다.
- `git diff --check` 통과. 두 저장소는 `feature/ai-spending-coach` 유지. 백엔드 설정 1개 변경, 문서/점검 SQL 2개 추가; 프론트 추적 파일 변경 없음.
- 실시간 Steam·Gemini·OpenAI 호출, BBS03 기능의 이식, DB 마이그레이션 실행, 신규 뉴스 저장, 실제 음성 재생은 수행하지 않았다. 뉴스·음성 기능의 완료 검증과 구분한다.
