# BBS04: 팀원 PC 실행과 음성 검색

현재 단계: 기존 게임 검색 + Steam 공식 소식 조회 + OpenAI 음성 입력(STT).
3-1단계 한국어 요약·낭독 대본 생성/저장을 추가했고 현재 로컬 DB의 추가 SQL 적용과 실제 GPT 1건 저장을 검증했다. 팀원 PC의 SQL 적용 및 설정은 [bbs-stage3-summary.md](bbs-stage3-summary.md)를 따른다. 낭독(TTS)과 음성 명령 해석은 아직 연결하지 않았다.

## Git, 로그인, DB의 관계

- Git은 코드와 설정 예시를 전달한다. `.env`, DB 데이터, 기존 게임 CSV는 전달하지 않는다. CSV는 `.gitignore`에서 제외한다.
- 현재 `ddl-auto=none`, `spring.sql.init.mode=never`다. 서버 시작이나 로그인으로 테이블이 생성되지 않는다.
- 앱 로그인은 연결된 DB의 사용자 데이터를 사용한다. 서로 다른 DB의 계정과 뉴스 데이터가 자동 공유되지 않는다.
- BBS 조회/음성 입력은 현재 `/api/games/**` 공개 정책을 따른다. 로그인이 필수인 다른 기능과 구분한다.
- 기존 DB를 사용하는 팀원은 게임과 검색명 테이블을 유지한다. 다른 DB의 전체 앱을 실행하려면 팀이 관리하는 최신 스키마 및 필요한 데이터를 먼저 복원한다. 예전 `gamebalance_schema_mysql8.sql`에는 현재 Entity와 다른 테이블이 있으므로 전체 초기화 기준으로 그대로 실행하지 않는다.

## 필요한 준비

1. JDK 21, 프로젝트 Vite가 지원하는 Node.js(22.12 이상 권장), MySQL 8, 프론트/백엔드 BBS04 코드를 준비한다.
2. 자신의 DB에 `games`, `game_search_names`와 검색할 게임을 준비한다. 기존 약 7만 게임 전체가 필요하면 팀의 카탈로그 데이터 파일을 별도로 전달받는다. 사용자·로그인 데이터는 게임 카탈로그와 별개다.
3. BBS만 빈 로컬 DB에서 확인하려면 `docs/db/bbs-local-demo.sql`을 검토하고 선택한 DB에 직접 실행한다. 이 파일은 검색용 테이블 2개와 게임 3개만 준비한다. 전체 앱/회원가입용 스키마는 아니다. 기존 행은 갱신하거나 삭제하지 않는다. 일반 서버 시작 시에는 실행되지 않는다.
4. 백엔드 루트의 `.env.example`을 `.env`로 복사해 다음 값을 채운다. `.env`는 Git에 올리지 않는다.

```dotenv
DB_URL=jdbc:mysql://localhost:3306/gamesense?serverTimezone=Asia/Seoul
DB_USERNAME=본인_DB_계정
DB_PASSWORD=본인_DB_비밀번호
JWT_SECRET_KEY=본인이_생성한_32바이트_이상의_비밀키
STEAM_API_KEY=본인_Steam_API_키
FRONTEND_BASE_URL=http://localhost:9191
OPENAI_API_KEY=본인_OpenAI_API_키
OPENAI_STT_MODEL=gpt-transcribe
SEED_GAMES_ENABLED=false
SEED_PRICES_ENABLED=false
FEATURED_SYNC_ON_STARTUP=false
FEATURED_SYNC_CRON=-
```

BBS 공식 뉴스 요청 자체에는 Steam API 키를 전송하지 않는다. 앱의 기존 Steam 기능은 해당 설정을 사용한다. BBS 음성 검색은 OpenAI API 프로젝트의 키·모델 접근 권한·사용 가능한 한도가 필요하다. Gemini/Groq 키는 BBS에서 사용하지 않는다.

5. 백엔드 루트에서 Windows는 `./gradlew.bat bootRun`, macOS/Linux는 `./gradlew bootRun`으로 실행한다. 기본 포트는 8080이다.
6. 프론트 루트에서 `npm ci`를 실행한다. `.env.local`에 `VITE_BACKEND_URL=http://localhost:8080`을 넣는다. 기본 개발 설정에서는 `VITE_API_BASE_URL`을 비워 같은 출처의 프록시를 사용한다.
7. `npx vite --host localhost --port 9191 --strictPort`로 실행하고 `http://localhost:9191/bbs`에 접속한다. 기존 `npm run dev`는 Windows 명령을 포함하므로 다른 OS에서는 이 명령을 사용한다.

서버/PC 주소가 달라지면 프론트 백엔드 주소와 허용 프론트 주소도 맞춰야 한다. 브라우저 마이크는 localhost 또는 HTTPS에서 권한을 허용해야 한다. 휴대폰에서 PC의 일반 HTTP IP 주소로 접속하는 방식은 음성 검증용으로 사용하지 않는다.

## 동작과 저장 범위

- 원형 파동을 0.2초 누르면 녹음을 시작한다. 0.2초 전에 놓거나 영역을 벗어나면 시작하지 않는다. 키보드 Enter/Space도 지원한다.
- 게임 이름을 말한 뒤 약 2초간 조용하면 자동 제출한다. 원형을 다시 0.5초 누르면 진행 중인 녹음/인식 요청을 취소하고 결과를 검색에 반영하지 않는다. 처음 10초간 소리가 없으면 취소하며 최대 녹음은 30초/4MB다.
- Escape, 페이지 이동, 탭 숨김, 수동 검색 입력은 진행 중 녹음/결과 반영을 취소한다. 마이크 권한이 늦게 허용되어도 취소된 세션의 마이크는 닫는다.
- OpenAI의 인식 결과를 기존 검색창에 넣는다. 현재 단계는 게임 이름 입력이며, “최신 패치 읽어줘” 같은 명령을 해석하지 않는다.
- 원형 아래 사용 안내는 실제 녹음(LISTENING) 중에만 표시한다. 평소, 권한 대기, 녹음 종료 후 인식 처리, 완료 및 취소 상태에는 숨긴다. 오류 안내는 필요한 경우 별도로 표시한다.
- `POST /api/games/bbs/voice/transcriptions`에 `audio` multipart 파일을 보내면 `{ "transcript": "..." }`를 받는다. WebM/MP4/WAV, 실제 파일 헤더, 크기, 결과 길이를 검증한다.
- 게임 DB, 별칭, 기존 뉴스/브리핑을 수정하지 않는다. 뉴스는 현재 서버 메모리 캐시다. STT 전용 Entity/테이블을 만들거나 원본 녹음/인식문을 DB에 저장하지 않는다. 오디오 처리에는 브라우저 메모리와 서버의 요청 임시 처리가 포함된다.
- API 키는 서버에만 보관한다. 제공자 오류 본문·키·녹음·인식문을 로그에 출력하지 않는다. 자동 재시도나 다른 AI 제공자로의 전환은 없다.
- 기본 호출 제한은 서버 인스턴스당 UTC 일 200회, 접속 주소당 분 6회, 동시 2회다. `app.bbs.voice.daily-calls`, `calls-per-minute`, `concurrent-calls`로 조정한다. 실패한 제공자 호출도 횟수에 포함한다. 메모리 제한이므로 재시작 시 초기화되며, 여러 서버의 공통 한도가 아니다. 프록시 환경에서는 실제 원격 주소 전달 설정에 따라 여러 사용자가 같은 주소 한도를 공유할 수 있다.

## 검증 범위

자동 테스트는 H2와 가짜 OpenAI 응답/가짜 브라우저 마이크를 사용한다. 기존 MySQL은 읽기 전용으로 확인하며 실제 유료 API 호출이나 사용자 마이크 녹음은 자동으로 실행하지 않는다. 실제 계정의 모델 권한, 한국어 음성 인식 정확도는 해당 PC에서 별도로 확인해야 한다.

API 규격: [OpenAI File transcription](https://developers.openai.com/api/docs/guides/speech-to-text).

### 확인 결과 — 2026-10-24

- 프론트 BBS ESLint, 빌드, 뉴스 모델 테스트 5개 및 녹음 수명주기 테스트 5개 통과.
- 백엔드 게임/게시판/뉴스/기동 회귀 테스트 및 새 STT 테스트 통과. 별도 빈 H2 DB에서 수동 준비 SQL의 생성·재실행·기존 행 보존 확인.
- 음성 브라우저 점검 11개, 기존 뉴스 브라우저 회귀 점검 15개 통과. 음성 요청은 가짜 마이크와 응답을 사용하며 게임 검색과 공식 뉴스 조회는 실제 로컬 백엔드로 확인.
- 임시 백엔드에서 키 미설정 시 503 및 `BBS_STT_NOT_CONFIGURED` 응답 확인. 유료 OpenAI 호출은 하지 않았다.
- 실제 DB 읽기 전용 대조: games 69,919건, game_search_names 133,365건, game_news/game_news_briefings 각 15건 유지. 신규 SQL은 실제 DB에 실행하지 않았다.
- 두 저장소 BBS04 유지. commit/push/merge 없음. 기존 프론트 번들 500kB 초과 경고는 남아 있다.
