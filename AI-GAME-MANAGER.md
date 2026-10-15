# AI 내 게임 매니저

## 사용자 흐름
`/ai-game-manager`에서 **내 게임 정리 / 할인 기다리기 / 알림**을 이용합니다.
- Steam 보유 게임 동기화 또는 직접 등록 → 자연어 요청 → AI 컬렉션 초안 수정 → 확인 후 저장.
- 구매할 게임·DLC 선택 또는 자연어 구매 조건 → 공식 상품·현재 가격 확인 → 원화 목표 금액 저장.
- 조건 충족 시 사이트 알림 저장. 읽음 표시, 감시 중지/재개, 조건 수정/삭제 제공.
- 첫 화면과 로그인 화면에 **데모로 둘러보기**. `?demo=1`은 샘플 보유 게임·가격·제안만 이용하며 실제 Gemini/감시 API를 호출하지 않습니다. 새로고침 시 초기화됩니다.
- AI 제안은 자동 저장되지 않습니다. AI 요청은 취소 가능하고, 저장 중에는 중복 입력을 막습니다. 동일 내용 재저장은 기존 항목을 반환합니다. 삭제는 확인 후 실행합니다.

## 실행 및 연결
프런트와 백엔드 모두 `codex/ai-game-manager` 브랜치. 프런트 기반 `codex/ai-taste-worldcup` 96d3533, 백엔드 기반 0e0de98이며 선행 AI/스크린샷/한글 테트리스 변경이 포함됩니다. 팀 병합 시 선행 변경 여부를 확인하세요. 이번 작업은 원격 업로드/PR/병합을 하지 않았습니다.

백엔드 `.env`의 `GEMINI_API_KEY`를 재사용합니다. `.env.example`의 `GEMINI_MANAGER_MODEL=gemini-3.5-flash-lite`는 모델 이름이며 비밀키가 아닙니다. 실제 키는 서버 전용이며 프런트 또는 Git에 포함하지 않습니다. DB/JWT/CORS/Steam 계정 연동은 기존 설정을 따릅니다. 프런트 개발 프록시는 기존 `AI_API_TARGET`을 사용합니다.

`db/ai-game-manager.sql`로 `ai_manager_records`와 누락된 경우 기존 엔티티에 맞는 `library_games`를 준비합니다. **users.id가 BIGINT UNSIGNED이면 두 user_id도 BIGINT UNSIGNED로 맞춘 뒤 적용하세요.** CREATE IF NOT EXISTS이므로 기존 테이블 구조를 변경하지 않습니다. 적용 전 실제 엔티티/기존 스키마 일치를 확인하세요. 이번 로컬 MySQL에서는 사용자 FK 및 ON DELETE CASCADE를 확인했습니다.

## API
공통 prefix `/api/ai/manager`, 기존 회원 JWT 필요.
- GET 루트: 보유 게임, 저장 항목, 237개 검색 카탈로그, 검사 간격.
- GET `/products/{appId}`: Steam 한국 상점 상품 확인.
- POST `/sync`, POST `/owned`: 기존 Steam 동기화 / 직접 보유 등록.
- POST `/draft`: `{mode: collection|purchase, message}` → Gemini 확인용 초안.
- POST `/collections`, PATCH `/collections/{id}`: 저장/이름 변경.
- POST `/watches`, PUT `/watches/{id}`: 상품 1~6개/목표 1~10,000,000원/이름 확인 후 저장.
- POST `/watches/{id}/check`, PATCH `/watches/{id}`: 즉시 검사/활성 상태.
- PATCH `/notifications/{id}/read`, DELETE `/records/{id}`: 읽음/본인 항목 삭제.
모든 저장 항목은 회원 소유권 검사 및 optimistic version 적용. 컬렉션에는 실제 조회된 보유 ID만 허용합니다. 직접 등록은 사용자가 입력한 보유 목록이며 Steam 라이선스 검증 자료가 아닙니다. 직접 등록 게임의 플레이 시간은 미확인으로 처리합니다.

## 가격 및 자동 알림
Steam 공개 상점 appdetails 응답을 사용합니다. 보유 목록 동기화는 기존 Steam API 연동과 공개 프로필 설정이 별도로 필요합니다.
- 한국 상점 KRW 최소 화폐 단위를 100으로 나누어 원화 표시. 통화 불일치/가격 누락은 저장 거부.
- 본편 또는 DLC만 지원. DLC는 본편 보유/구매 포함 여부 검사. 번들·패키지·에디션 구성 자동 해석 및 사운드트랙 지원 없음. 개별 상품 합계를 번들 결제 가격으로 표시하지 않습니다.
- 보유한 상품 가격 제외. 모든 선택 상품을 보유한 조건은 저장 불가.
- 상품 조회 8초/연결 5초 제한, 5분 캐시(최대 약 1,000개). 외부 응답 장애 시 이전 확인 가격과 충족 상태 보존 및 오류 표시.
- 서버 실행 중 기본 60분 간격, `APP_MANAGER_CHECK_INTERVAL_MINUTES`로 변경 가능. `APP_MANAGER_SCAN_DELAY_MS` 기본 60,000ms, 한 번에 최대 15개 감시 기록을 순환 검사하므로 규모/외부 지연에 따라 추가 대기 가능.
- 처음 충족/미충족 후 재충족/재활성화 시 알림. 같은 조건의 연속 검사 또는 동일 조건 수정은 중복 알림을 만들지 않습니다.
- 프런트 로그인 상태에서 60초마다 알림/저장 정보를 갱신합니다. 이메일·브라우저 푸시·자동 구매는 제공하지 않습니다.
- AI 요청은 회원당 분당 6회, 기존 AI와 동시 실행 1개 공유. 컬렉션 초안은 앞의 보유 게임 100개를 사용합니다. 직접 등록 최대 500개, 컬렉션 50개, 구매 대기 20개.

## 2026-10-12 검증
- 백엔드 대상 53개 실패/오류 0 (매니저 16개 + 기존 AI/월드컵 37개). 팀 전체 테스트 통과를 의미하지 않습니다.
- 프런트 전체 37개 통과, ESLint/build 통과. Vite 500KB 청크 경고는 남아 있습니다.
- 공식 Steam Terraria/Portal 2/Elden Ring/Hades 실제 상품·가격 및 Elden Ring DLC 2778580의 본편 관계 확인.
- 실제 Gemini 컬렉션 초안과 Satisfactory 30,000원/Hades 20,000원 구매 초안 확인.
- 실제 MySQL 저장/재조회, 중복 알림 방지, 읽음 및 서버 자동 알림 확인. 자동 검사 검증은 일회용 회원의 마지막 검사 시각을 과거로 설정한 테스트 조건이며 실제 할인 이벤트가 아닙니다.
- 실제 브라우저 로그인 → AI 컬렉션 수정/저장 → 구매 초안 → 잘못된 금액 안내 → 저장 → 감시 중지 → 알림 읽음 → 요청 취소/입력 유지 → 미연동 Steam 안내 확인.
- 로그인 없이 데모 입구/컬렉션/구매/알림 샘플 및 모바일 390px 가로 넘침 검증.
- 실제 Steam 계정이 연결된 사용자의 보유 동기화는 이번 일회용 회원으로 검증하지 않았습니다. 기존 연결을 재사용하며 미연동 오류 안내는 확인했습니다.
- 외부 Steam/Gemini 가용성과 실시간 할인 여부는 보장하지 않습니다. 마지막 확인 시각/상점 최종 가격을 함께 표시합니다.

증빙은 작업 폴더 outputs의 manager-collection-gemini.json, manager-purchase-gemini.json, manager-live-check.json, manager-steam-dlc.json 및 화면 PNG에 있습니다. 테스트 회원/인증 파일은 검증 후 정리합니다.
