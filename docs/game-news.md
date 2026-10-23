# Steam 공식 소식 수집과 한국어 AI 브리핑

기존 `games.app_id`를 참조하는 `domain.gamenews` 기능이다. `board`와 기존 `news_articles`는 사용하지 않는다.
현재 범위는 원문 수집·저장, 한국어 AI 브리핑 생성·재생성 및 상태 관리, 읽기 전용 목록·상세 API와 주기 실행이다.
프론트엔드의 게임 탐색 → BBS(`/bbs`)에서 저장된 브리핑을 읽는다.

## 주기 실행

Spring 백엔드가 실행 중일 때 동작한다. PC/백엔드가 꺼져 있으면 실행되지 않으며, 다시 켜면 다음 점검에서 밀린 작업을 처리한다.
소스 기본값은 비활성화이고, 로컬 `.env`에서 다음 설정으로 기존 3개 게임만 활성화한다.

```dotenv
NEWS_SYNC_ENABLED=true
NEWS_SYNC_APP_IDS=570,730,440
NEWS_SYNC_NEWS_COUNT=5
NEWS_SYNC_BRIEFING_BATCH_SIZE=5
NEWS_SYNC_POLL_INTERVAL=PT5M
NEWS_SYNC_INITIAL_DELAY=PT30S
NEWS_SYNC_REQUEST_DELAY_MS=1000
```

- 시작 30초 후 첫 점검, 실행이 끝난 뒤 5분마다 다시 점검한다. 설정 변경은 백엔드 재시작 후 적용한다.
- Steam 재수집은 각 게임의 `next_collect_at`이 되었을 때만 실행한다. 성공하면 다음 수집은 6시간 후다.
- 현재는 게임별 **최신 공식 소식 5개**를 다시 확인한다. 한 번에 5개를 넘는 신규 소식이나 범위 밖 과거 글의 변경은 놓칠 수 있다. 필요하면 `NEWS_SYNC_NEWS_COUNT`(1~100)를 늘린다.
- 저장된 원문 중 새 소식(아직 브리핑 없음), 원문 해시가 달라진 소식, 생성 실패/중단 후 처리 가능한 소식만 AI 대상이다.
- 동일 원문의 성공 브리핑은 생성 시각/내용을 유지한다. 모델명이나 프롬프트 버전만 바뀌어도 **자동 재생성하지 않는다**. 기존 명시적 `briefGameNews` 명령의 버전 갱신 동작은 유지한다.
- 브리핑 후보는 최신 5개뿐 아니라 대상 게임의 저장된 전체 소식에서 찾는다. 오래된 누락/실패도 게임당 한 차례 최대 5개씩 처리하며 다음 점검에서 이어 간다.
- Steam 수집 실패/수집 대기 중에도 저장된 소식의 AI 재시도를 진행한다. 한 게임·글의 실패가 다른 게임·글을 막지 않는다.
- 수집/AI 실패의 재시도는 기존 5분부터 최대 6시간 지수 대기를 따른다. 실제 실행은 그 시각 이후 첫 점검에서 이뤄진다.
- 게임별 수집 선점은 부모 게임 행을 잠그는 짧은 트랜잭션으로 처리한다. 여러 서버가 동시에 점검해도 같은 게임의 정상 수집은 한 번만 시작한다.
- `next_collect_at`은 수집 중에는 10분 선점 만료 시각이다. 중단 후 만료되면 다시 선점할 수 있다. 완료/실패 시 정상 주기/재시도 시각으로 바꾼다.
- 늦게 도착한 이전 수집 결과는 `last_attempt_at` 선점 토큰으로 거절한다. AI도 기존 원문 행 잠금, 10분 선점과 원문 해시 재확인을 유지한다.
- 같은 서버의 주기 실행이 겹치면 건너뛴다. 전용 `game-news-` 스레드를 사용해 다른 도메인의 예약 작업을 막지 않는다. HTTP/AI 대기 중 DB 트랜잭션을 열어 두지 않는다.
- 로그 `News sync app_id=...`에 수집 여부, 신규/변경/동일 건수, 브리핑 결과와 오류 건수를 남긴다. 저장 상태는 기존 3개 테이블을 사용하며 스키마 추가 변경은 없다.

예약 실행 자체를 실제 DB에서 짧게 검증하려면:

```powershell
.\gradlew.bat verifyGameNewsSchedule "-PnewsAppIds=570,730,440" --console=plain
```

이 명령은 **명시한 게임의 다음 수집 시각을 현재로 당긴 뒤** 별도 제한된 프로세스에서 실제 예약 실행을 두 번 기다리고 자동 종료한다.
검증 프로세스의 점검 간격만 5초이며 운영 설정은 바꾸지 않는다. 실제 Steam/필요한 Gemini 호출과 DB 저장을 수행한다.
원문을 임의로 변경하지 않는다. 동일 원문의 이전 브리핑 보존, 두 번째 실행의 불필요한 수집·AI 생략, 실제 AI 호출 수를 확인한다.
게임/게시판 데이터 건수는 보존하며, 수집 상태와 `last_seen_at`은 정상적으로 갱신된다.

## 읽기 전용 조회 API

기존 공개 게임 API(`/api/games/**`)의 보안 규칙을 따른다. 로그인 없이 GET으로 조회하며 등록·수정·삭제 API는 없다.
조회 시 수집·Gemini 호출·브리핑 재생성·조회수 갱신을 하지 않는다.

| 용도 | 경로 |
|---|---|
| 전체 또는 게임 필터 목록 | `GET /api/games/news?appId=570&category=PATCH&page=0&size=20` |
| 게임별 목록 | `GET /api/games/570/news?category=PATCH&page=0&size=20` |
| 게임별 소식 상세 | `GET /api/games/570/news/{newsId}` |

- 전체 목록의 `appId`는 선택값이다. 내부 `games.app_id`이자 Steam appid를 그대로 사용한다.
- `newsId`는 `game_news.id`이다. Steam 원문 `gid`와 구분한다.
- `category`: `NOTICE`(공지), `PATCH`(업데이트·패치), `EVENT`(이벤트), `MAINTENANCE`(점검·장애), `DEV_NEWS`(개발 소식), `OTHER`(기타). 생략/빈 값은 전체이며 대소문자를 구분하지 않는다.
- `page`: 0부터 시작, 기본 0. `size`: 1~100, 기본 20. 조회 offset은 정수 범위로 제한한다.
- 정렬은 원문 `publishedAt` 내림차순, 동일 시각이면 `newsId` 내림차순이다. 게시 시각이 없는 글은 뒤에 온다.
- 페이지 응답: `content`, `page`, `size`, `totalElements`, `totalPages`, `first`, `last`.
- 목록 항목: `newsId`, `appId`, `gameName`, `title`, `category`, `summary`, `sourceUrl`, `publishedAt`, `sourceUpdatedAt`, `generatedAt`, `generationStatus`, `stale`.
- 상세는 목록 항목에 한국어 `aiContent`를 추가한다. `title`은 Steam 원문 제목이며 AI가 번역한 제목이 아니다.
- 날짜는 UTC ISO 8601(`2026-10-24T00:00:00Z`)이다. `sourceUpdatedAt`은 원문의 변경을 저장한 시각이다.
- 성공 본문·요약·분류·생성 시각이 있는 브리핑만 공개한다. 최초 생성 대기/실패 또는 원문만 있는 소식은 목록에서 제외한다.
- 재생성 대기/실패 중에도 이전 성공본은 조회할 수 있다. 현재 원문과 생성 기준이 다르면 `stale=true`이며 프론트는 갱신 대기 표시를 할 수 있다.
- 원문 본문, 해시, 모델명, 내부 오류, 재시도 정보는 반환하지 않는다. 목록 쿼리는 긴 원문·AI 본문을 읽지 않으며 항목별 추가 조회가 없다.
- `summary`/`aiContent`는 텍스트로 렌더링한다. Markdown 렌더러를 사용할 경우 HTML 실행을 차단한다.

오류 응답은 기존 `{ "code": "...", "error": "..." }` 형식을 따른다.

| HTTP | 코드 | 조건 |
|---|---|---|
| 400 | `INVALID_GAME_NEWS_QUERY` | 잘못된 번호·분류·페이징 |
| 404 | `GAME_NOT_FOUND` | DB에 없는 게임 |
| 404 | `GAME_NEWS_NOT_FOUND` | 소식 없음, 해당 게임 소식이 아님, 공개 가능한 브리핑 없음 |

존재하는 게임에 브리핑이 없거나 마지막 페이지를 넘으면 HTTP 200과 빈 `content`를 반환한다.
목록 조회의 동시 수집에 대해서는 일반 offset 페이징이므로 여러 요청 간 스냅샷은 보장하지 않는다.

구조는 `controller` → `service`(인터페이스/구현체) → `repository`이며 응답은 `dto`의 record를 사용한다.
Swagger 설명은 기존 게임 도메인처럼 `controller/docs/GameNewsApiDocs`에 있다.
`SteamNewsException`, `BriefingGenerationException`을 포함한 뉴스 예외는 `exception` 패키지에 모았다.
잘못된 숫자 변환의 응답 처리도 이 컨트롤러에만 적용한다. 기존 `board` 및 전역 보안/예외 코드는 변경하지 않는다.

## 실행

백엔드 저장소 루트의 `.env` 또는 환경변수에 기존 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`를 사용한다.
Java 21과 기존 `games` 및 뉴스 테이블이 필요하다. 수집 명령은 스키마를 검증만 하며 테이블을 생성/변경하지 않는다.

```powershell
.\gradlew.bat collectGameNews "-PnewsAppIds=570,730,440" -PnewsCount=5 --console=plain
```

- DB에 존재하는 app_id를 1~20개 명시한다. 하나라도 존재하지 않으면 수집 시작 전에 중단한다.
- `newsCount`는 게임별 최신 소식 개수이며 기본 20, 범위 1~100이다. 게임 사이에는 1초 간격을 둔다.
- 같은 명령을 다시 실행하면 `inserted=0`, `unchanged=N`으로 중복 방지를 확인할 수 있다. 실제 원문이 바뀌면 `updated`가 증가한다.
- 결과의 `stale=true`는 더 늦게 시작된 수집이 먼저 반영되어 해당 응답을 무시했다는 뜻이다.
- 실패는 게임별로 기록하고 다음 게임을 처리한다. 하나라도 실패하면 명령은 실패 종료한다.
- 원문 수집 CLI는 별도 소스셋이므로 웹 앱/bootJar에 포함되지 않는다. 웹 서버, 기존 시작 작업, 스케줄러, AI 컴포넌트를 실행하지 않는다.
- 서버 내부에서는 `GameNewsCollectionService.collect(appId, count)`를 명시적으로 호출한다. HTTP 대기 중 DB 트랜잭션을 유지하지 않도록 기존 트랜잭션 안에서 호출하면 거절한다.
- 명시적 재수집은 `next_collect_at`을 무시한다. 자동 수집은 해당 시각과 작업 선점을 확인한다.

## Steam 응답과 수집 범위

[Steam 공식 ISteamNews 문서](https://partner.steamgames.com/doc/webapi/ISteamNews)의 `GetNewsForApp/v2`를 사용한다.
`appid`, `feeds=steam_community_announcements`, `maxlength=0`을 전달한다. 공개 API이며 API 키를 보내지 않는다.
응답 전체 및 각 소식의 appid를 검증하고 공식 피드만 저장한다. HTTP 오류, 필수 필드 누락, 잘못된 응답을 빈 성공으로 처리하지 않는다.
원문의 HTML/BBCode는 제거하지 않고 보존한다. 향후 화면에서 출력할 때는 별도의 안전한 렌더링 처리가 필요하다.

매 실행마다 최신 N건을 다시 읽어 신규 등록과 기존 원문의 변경을 함께 확인한다.
현재 과거 전체 이력 수집/페이지 순회는 없다. 최신 N건 밖으로 밀려난 글의 수정·누락과 삭제 여부는 감지하지 않는다.
공식 피드에도 홍보·방송 소식이 포함될 수 있으며 종류는 AI 브리핑 생성 단계에서 분류한다.

## 테이블과 저장 규칙

- `game_news`: Steam 원문. `(app_id, source_news_id)` 유일 제약. `source_news_id`는 Steam `gid`를 손실 없이 저장하는 문자열이다.
- `game_news_briefings`: `news_id`가 기본키이자 원문 외래키인 최신 브리핑. AI 생성 시 만들며 원문이 바뀌면 이전 성공본을 보존한 채 PENDING으로 전환한다.
- `game_news_sync_state`: `app_id`가 기본키이자 게임 외래키인 수집 상태. 실제로 수집을 시도한 게임에만 생성한다.

`docs/db/game_news.sql`은 MySQL 8용 추가 스키마다. 기존 `games(app_id)`에 RESTRICT 외래키를 사용하며 cascade는 없다.
모든 저장 시각은 UTC이다. SQL 직접 입력 시 필수 시각, 상태, 카운터를 명시해야 한다.

동일 게임은 저장 트랜잭션 안에서 부모 `games` 행을 잠근 후 원문 및 상태를 조회/갱신한다.
게임 필드는 수정하지 않는다. 여러 프로세스가 최초 수집을 동시에 수행해도 뉴스와 상태가 중복 삽입되지 않는다.
더 오래된 요청의 응답/실패가 뒤늦게 도착하면 이후 요청에서 반영한 상태를 덮어쓰지 않는다.
한 게임의 원문과 성공 상태는 함께 커밋한다. 저장 중 실패하면 해당 게임의 변경을 롤백하고 별도 트랜잭션에 실패를 기록한다.
DB 자체가 끊겨 실패 기록도 불가능하면 원래 예외를 호출자에게 전달한다.

## 변경 감지와 상태

제목, 본문, 링크, UTC 게시 시각을 순서대로 SHA-256 처리한다. 각 UTF-8 필드 길이를 포함하고 줄바꿈만 LF로 통일한다.
원문 저장값은 보존하며 공백, HTML/BBCode 등을 임의로 제거하지 않는다.

- 신규: 원문과 해시, `collected_at`, `last_seen_at`, `updated_at` 저장.
- 동일 해시: `last_seen_at`만 갱신.
- 다른 해시: 같은 ID 행의 원문·해시·`updated_at` 갱신. `collected_at` 유지.
- `updated_at`은 Steam이 제공한 수정 시각이 아니라 변경을 감지한 시각이다.
- 성공: 시도·성공 시각, 다음 수집 시각(+6시간), 실패 횟수 0, 오류 해제. 소식이 0건이어도 정상 응답이면 성공이다.
- 실패: 시도 시각과 오류, 연속 실패 횟수 저장. 기존 성공 시각과 원문 유지. 재시도 간격은 5분부터 두 배씩 증가해 최대 6시간이다.

## 기존 Repository 계약

`GameNewsRepository`는 게임+Steam 공지 ID 및 게임별 최신순 페이지를 조회한다.
`GameNewsBriefingRepository.findPublished`는 재생성 실패 시에도 이전 성공 결과를 노출할 수 있다.
조회 API는 `findPublishedSummaries`/`findPublishedDetail`의 projection을 사용해 응답에 필요한 열만 읽는다.
`findReadyForGeneration`은 PENDING 및 재시도 시각이 지난 FAILED를 조회하며 작업 선점 기능은 없다.
`GameNewsSyncStateRepository`는 다음 수집 시각이 지난 게임을 조회한다. 자동 실행은 설정된 게임만 대상으로 `collectIfDue`를 통해 선점한다.
`GameNewsRepository.findBriefingCandidates`는 생성 필요·재시도 가능·중단 복구 대상만 찾는다. 작업 선점 시 원문 해시를 다시 확인한다.

## 검증

```powershell
.\gradlew.bat test --tests "org.ict.final_project_back_end.domain.gamenews.*" --tests "org.ict.final_project_back_end.domain.board.*" --tests "org.ict.final_project_back_end.domain.aicoach.client.*" --console=plain
```

자동 테스트는 격리된 H2 DB와 모의 Steam 응답을 사용한다.
API 요청·공식 피드 필터·ID/필수 필드 검증·전송 실패, 재수집 중복 방지, 원문 변경,
동시 최초 저장, 오래된 응답 차단, 실패 롤백/재시도/회복 및 기존 게시판 회귀를 확인한다.
실제 수집 검증은 위의 `collectGameNews` 명령을 명시적으로 실행한다.
저장된 브리핑의 HTTP 조회만 검증하려면 다음 명령을 사용한다.

```powershell
.\gradlew.bat verifyGameNewsApi "-PnewsAppIds=570,730,440" --console=plain
```

검증용 서버를 `127.0.0.1`의 임시 포트에서 시작하고 자동 종료한다. 실제 조회 컨트롤러·서비스·보안 설정을 사용한다.
DB 연결은 읽기 전용, 스키마는 validate이며 수집/AI 컴포넌트·기존 시작 작업·스케줄러를 포함하지 않는다.
전체·게임별 목록, 전체 페이지 순회, 모든 상세 본문과 DB 내용 일치, 분류, UTC, 400/404 및 데이터 불변을 검사한다.
이 명령은 소량 데이터 점검용(대상 게임별 공개 브리핑 1~100개)이며 뉴스 3개 테이블의 검증 전후 스냅샷을 비교한다.

## 한국어 AI 브리핑

기존 `GeminiCoachClient`의 연결·타임아웃·HTTP 재시도를 그대로 재사용한다. 기존 코치/분석 프롬프트는 변경하지 않는다.
뉴스 전용 `GeminiNewsBriefingClient`가 공식 원문 한 건의 분류, 한국어 요약, 한국어 본문을 생성하고 검증한다.
기존 `GEMINI_API_KEY`, `GEMINI_COACH_MODEL`(기본 `gemini-3.5-flash-lite`), `GEMINI_BASE_URL` 설정을 사용한다.
웹 앱 내부에서는 기존 `app.coach.*` 설정을 공유하며 API 키와 AI 응답 원문을 로그/DB 오류 메시지에 기록하지 않는다.

이미 저장된 최신 N건의 브리핑만 생성:

```powershell
.\gradlew.bat briefGameNews "-PnewsAppIds=570,730,440" -PnewsCount=5 --console=plain
```

Steam 재수집 후 신규/변경 원문의 브리핑까지 생성:

```powershell
.\gradlew.bat syncGameNews "-PnewsAppIds=570,730,440" -PnewsCount=5 --console=plain
```

`collectGameNews`는 계속 원문 수집만 담당한다. 수정한 원문의 기존 브리핑은 PENDING으로 표시하며 AI를 즉시 호출하지 않는다.
`syncGameNews`를 사용하면 수집 직후 브리핑 생성까지 이어진다. `briefGameNews`는 Steam API를 호출하지 않는다.
두 명령은 게임을 명시해서 일회성으로 실행한다. 백엔드의 정기 실행은 위의 `NEWS_SYNC_*` 설정으로 별도 활성화한다. 뉴스 입력은 외부 Gemini로 전달되며 사용자 개인 데이터는 포함하지 않는다.

- 최초 생성: 분류(`category`), 요약(`summary`), 본문(`ai_content`), 기준 해시, 설정 모델명, 프롬프트 버전, UTC 생성 시각을 저장한다.
- 같은 원문 해시·모델·프롬프트의 성공본이 있으면 `UP_TO_DATE`로 건너뛰며 AI를 다시 호출하지 않는다.
- 원문 변경: 수집 트랜잭션에서 PENDING으로 전환한다. 다음 브리핑 처리에서 같은 `news_id` 행을 재생성한다.
- 생성 중: 짧은 DB 트랜잭션으로 작업을 선점하고 AI 응답을 기다리는 동안 DB 잠금을 유지하지 않는다.
- 응답 저장 전 원문 해시와 작업 시작 시각을 재검증한다. 생성 중 원문이 바뀌거나 다른 작업자가 재선점했으면 이전 응답을 버린다.
- PROCESSING이 10분 이상 지속되면 다음 실행이 재선점할 수 있다. 정상 진행 중인 동일 뉴스는 `BUSY`로 건너뛴다.
- 실패: FAILED와 안전한 오류 코드, 실패 횟수, 재시도 시각을 기록한다. 5분부터 두 배씩, 최대 6시간 간격이다.
  재시도 시각 전에는 `DEFERRED`로 건너뛴다. 원문 변경으로 새로 PENDING이 되면 실패 카운터와 대기를 초기화한다.
- 재생성 실패 시 이전 성공 요약/본문/분류/기준 해시/생성 시각을 보존한다. 첫 생성 실패에는 임의의 대체 본문을 넣지 않는다.
  조회 API의 `generationStatus`와 `stale`로 이전 성공본이 최신 원문과 다를 수 있음을 표시할 수 있다.
- 기존 `findReadyForGeneration` 조회는 선점 기능이 아니다. 반드시 `GameNewsBriefingService.generate`의 선점/완료 흐름을 사용한다.

AI 출력은 JSON 형식, 허용 분류, 한국어 포함 여부, 길이, HTML 태그 부재를 검사하고 완결되지 않은 응답은 실패 처리한다.
원문에 없는 일정·수치·보상을 추측하지 않도록 프롬프트에 명시한다. 이 검증이 요약의 사실성을 완전히 보장하지는 않는다.
원문은 최대 100,000자까지 전달한다. 비어 있거나 더 긴 원문은 자르지 않고 `SOURCE_EMPTY`/`SOURCE_TOO_LONG`으로 실패 기록한다.
Gemini JSON 모드와 응답 봉투 형식은 [공식 API 문서](https://ai.google.dev/api/generate-content)를 따른다.

브리핑 확인:

```sql
SELECT n.app_id, n.title, b.category, b.summary, b.ai_content, b.generation_status,
       (b.generated_from_hash = n.content_hash) AS matches_current_source,
       b.model_name, b.prompt_version, b.generated_at, b.retry_count, b.last_error
FROM game_news n JOIN game_news_briefings b ON b.news_id = n.id
WHERE n.app_id IN (570, 730, 440)
ORDER BY n.app_id, n.published_at DESC;
```

## 실제 DB 검증 결과

2026-10-24(KST) 로컬 `final_project_db` 원문 수집 검증 결과:

| 게임 | app_id | 최초 저장 | 동일 조건 재수집 |
|---|---:|---:|---|
| Dota 2 | 570 | 5건 | 신규 0 / 동일 5 |
| Counter-Strike 2 | 730 | 5건 | 신규 0 / 동일 5 |
| Team Fortress 2 | 440 | 5건 | 신규 0 / 동일 5 |

원문 변경은 모의 응답과 격리 DB 테스트로 검증했으며 실제 Steam 원문을 임의 수정하지 않았다.
원문 수집 단계에서는 뉴스와 기존 게시판 테스트 총 81개가 통과했다.

같은 날 AI 연결 후 기존 `gemini-3.5-flash-lite`로 원문 15건을 실제 생성·저장했다.

| 게임 | app_id | 한국어 브리핑 성공 | 현재 원문 해시 일치 | 동기화 재실행 |
|---|---:|---:|---:|---|
| Dota 2 | 570 | 5건 | 5건 | UP_TO_DATE 5건 |
| Counter-Strike 2 | 730 | 5건 | 5건 | UP_TO_DATE 5건 |
| Team Fortress 2 | 440 | 5건 | 5건 | UP_TO_DATE 5건 |

실제 DB는 원문 15건, 수집 상태 3건, 브리핑 15건이며 모든 브리핑이 SUCCEEDED이다.
재실행 시 추가 AI 호출 없이 기존 생성 시각과 내용을 유지했다. 기존 게임 69,919건과 게시판 0건은 보존했다.
원문 변경 재생성, 생성 중 변경, 동시 선점, 중단된 작업 재선점, 실패/회복 및 기존 AI/게시판 회귀를 포함한 104개 테스트와 bootJar 빌드가 통과했다.
실제 Steam 원문을 임의로 변경하지 않았으며 원문 변경 시나리오는 격리 DB와 모의 AI로 검증했다.

같은 날 읽기 전용 API 추가 후 검증:

- 새 API 통합 테스트 11개를 포함해 뉴스·기존 게시판·기존 AI 클라이언트 테스트 115개 통과(실패/오류/건너뜀 0).
- `newsCollectorClasses`와 `bootJar` 빌드 통과.
- 실제 MySQL에 저장된 Dota 2(570), Counter-Strike 2(730), Team Fortress 2(440)의 브리핑 각 5건, 총 15건을 HTTP 목록·상세로 확인.
- HTTP 검사 55개 통과: 전체/게임 필터 경로 일치, 페이지 순회, 분류, 상세 한국어 본문과 저장 내용 일치, UTC, 400/404.
- 검증 전후 뉴스 원문·브리핑·수집 상태의 모든 행이 동일했고 게임/게시판 건수도 동일했다. 추가 Steam/Gemini 호출 없음.
- 테스트로 최초 생성 미완료 숨김, 재생성 실패 시 이전 성공본과 `stale`, 다른 게임의 소식 차단, 변경 메서드 405, 목록 쿼리 수를 확인.
- 기존 게시판 소스·테스트 58개 파일은 수정하지 않았다. 검증용 임시 서버는 확인 후 종료한다. 일반 API는 백엔드 실행/재시작 후 기본 포트 8080에서 사용한다.

같은 날 주기 실행 추가 후 검증:

- 뉴스·기존 게시판·기존 AI 클라이언트 테스트 128개 통과(실패/오류/건너뜀 0), `newsCollectorClasses`와 `bootJar` 빌드 통과.
- 새 통합 테스트로 신규/변경만 생성, 동일 원문과 모델/프롬프트 변경 생략, 수집 실패 중 AI 처리, AI 재시도 대기, 오래된 미생성 글의 배치 처리, 작업 중단 복구, 수집 동시 선점과 늦은 응답 거절을 확인했다.
- 실제 `@Scheduled` 실행 두 차례를 기존 3개 게임으로 확인했다. 첫 실행은 각 5건 수집, 신규 0·변경 0·동일 5였고 두 번째 실행은 세 게임 모두 `NOT_DUE`였다.
- **실제 AI 호출 0회**. 기존 성공 브리핑 15건의 본문·요약·생성 시각·생성 기준 해시·모델·프롬프트는 그대로였고 현재 원문과 모두 일치했다.
- 게임 69,919건과 기존 게시판 0건을 보존했다. 실제 원문 변경은 관측되지 않았으며 변경 재생성은 격리 DB/모의 AI 테스트로 확인했다.
- 로컬 `.env`에서 위의 3개 게임 설정을 활성화하고 백엔드를 재시작했다. 2026-10-24 02:07 KST 첫 점검에서 각 게임의 `NOT_DUE`, 오류 0을 실제 전용 스레드 로그로 확인했다.
- 검증 직후 다음 Steam 수집 시각은 세 게임 모두 2026-10-24 08:05 KST 이후다. 실제 실행은 해당 시각 이후 첫 5분 점검에서 시작한다.
- 재시작한 8080 서버의 목록·상세 API에서 한국어 브리핑 15건(SUCCEEDED, stale=false)을 다시 확인했고 기존 BBS 주소도 HTTP 200으로 응답했다.

Workbench 조회 예시:

```sql
SELECT g.app_id, g.name, COUNT(n.id) AS news_count
FROM games g LEFT JOIN game_news n ON n.app_id = g.app_id
WHERE g.app_id IN (570, 730, 440)
GROUP BY g.app_id, g.name;

SELECT id, app_id, source_news_id, title, published_at, collected_at, updated_at, last_seen_at
FROM game_news WHERE app_id IN (570, 730, 440)
ORDER BY app_id, published_at DESC;

SELECT * FROM game_news_sync_state WHERE app_id IN (570, 730, 440);
```
