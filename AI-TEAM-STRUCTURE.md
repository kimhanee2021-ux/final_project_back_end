# 팀 기준 AI 기능 구조 정리

2026-10-13. 로컬 브랜치 codex/ai-team-structure. 원격 업로드 및 팀 develop 병합은 하지 않았다.

## 기준 코드
- 프런트 팀 develop 0bda262 (test/front_back_connect와 동일).
- 백엔드 팀 develop 4cf1121.
- 기존 업로드된 codex/ai-game-manager 브랜치를 보존하고 정리 브랜치에서 최신 팀 코드를 로컬 통합했다.

## 프런트
- src/pages: 페이지 진입점. 기능 구현을 직접 모아두지 않는다.
- src/features/ai-games: 추천/구매 상담의 api.js, components/AiGames.jsx, hooks/useAiChat.js.
- src/features/ai-screenshots: api.js, components/ScreenshotAnalysis.jsx 및 SampleScreen.jsx, hooks/useScreenshotWorkspace.js, mocks/screenshotSamples.js, uploadModel.js.
- src/features/ai-worldcup: api.js, components/TasteWorldcup.jsx 및 GameCard.jsx, hooks/useWorldcup.js, worldcupModel.js, mocks/worldcupDemo.js.
- src/features/ai-manager: api.js, components/GameManager.jsx, hooks/useGameManager.js, managerModel.js, mocks/managerDemo.js.
- src/features/games/aiDemoCatalog.js 및 mocks/ai-demoGames.json, ai-demoGroups.json: 월드컵/매니저가 함께 사용하는 실제 게임 메타데이터. 매니저가 월드컵 구현을 참조하지 않는다.
- src/features/ai-common: 팀 utils/apiClient를 재사용하는 공통 요청 처리. multipart boundary, Axios 취소 -> AbortError, 상태 코드와 사용자 오류 문구 유지. AI 요청은 기존 화면의 시간 제한과 취소 로직을 사용하므로 공통 15초 제한을 적용하지 않는다.
- src/features/hangul-blocks: 게임 화면/엔진/키보드 처리. src/pages/blocks는 진입점이며 백엔드 없는 게임이다.
- AI/테트리스 스타일은 src/styles.
- 공통 인증은 팀 features/auth/context. 실제 회원과 비회원 데모를 구분하며 기존 임시 AI 로그인은 사용하지 않는다.

## 백엔드
- domain/aiGame: controller/dto/service. 추천과 구매 상담은 같은 대화 기능이므로 함께 관리.
- domain/aiScreenshot: 전용 controller/dto/service. 기존 추천 controller에서 업로드 endpoint를 분리했다.
- domain/aiWorldcup: controller/dto/service.
- domain/aiManager: controller/dto/entity/repository/service.
- domain/aiCommon: 공통 exception, 단일 AiRateLimit, GeminiSupport 통신/검증 기반.
- GeminiGameClient/GeminiScreenshotClient/GeminiWorldcupClient 및 ManagerAiClient가 각 기능의 프롬프트와 응답 처리를 소유한다.
- 요청/응답 DTO를 controller/service 내부에서 해당 기능 dto로 분리했다.
- 백엔드 테스트도 aiGame/aiWorldcup/aiManager 기능별로 이동했다.
- 팀 최신 BusinessException(status,code,message) 규칙에 AI 예외 코드를 연결했다.
- API 주소/JSON/기존 DB 테이블/인증 및 요청 제한 규칙 유지. 이번 구조 정리를 위한 추가 DB 변경 없음.

## 검증
- 프런트 Node 테스트 40개 통과. ESLint 오류 0, Vite 빌드 통과.
- 백엔드 전체 테스트 128개, 실패/오류 0. 기존 게시판/팀 기능도 포함했다.
- 실제 MySQL 서버 기동, 공통 로그인/me, 월드컵 카탈로그237개, 게임 검색, Steam 보유 직접 등록, 컬렉션 저장/재조회 확인.
- 브라우저 공통 로그인 후 AI 화면 진입, 매니저 데모24/8/12/8 및 읽음4->3, 월드컵 3회 데모 결과, 스크린샷 실제 원본/샘플 결과, 테트리스 시작/오른Shift 즉시낙하(점수0->26), 로그아웃 확인.
- 실제 Gemini 매니저 컬렉션 제안과 구매 상담 성공. 추천 첫 요청 및 재확인에서 Gemini HTTP503 혼잡이 발생한 기록도 보존. 외부 AI 가용성을 보장하지 않는다.
- 실제 Terraria JPG 업로드, 미리보기, 새 Axios multipart 연결, Gemini 실제 분석 결과 반환까지 브라우저에서 확인했다.
- 임시 검증 계정과 종속 기록은 회원 삭제 API 204로 정리하고 이후 로그인 401 확인. 인증 임시파일 삭제 완료.
- 독립 코드 검토: 요청/취소 로직의 hooks 분리 지적을 수정했고, 재검토에서 해당 회귀 없음.
- Vite 기존 500KB 이상 청크 경고는 남아 있다.

## 공지사항 재확인
공지사항은 별도 브랜치를 보존했다. 백엔드 notice 역할별 폴더는 있지만, 프런트 pages/notices에 API/mock/style가 모여 있고 noticeToken 독립 로그인 흐름을 사용한다. 최신 팀 공통 인증/API 구조에 맞춘 추가 정리가 필요하며, 최신 BusinessException 오류 코드 생성자 호환도 필요하다. 이번 AI 정리에 공지 코드를 임의로 섞지 않았다.

## 실행
기존 환경 변수 및 AI-GAME-MANAGER.md, AI-WORLDCUP.md, AI-GAMES.md의 기능 설정을 사용한다. 실제 .env와 Gemini 키는 추적하지 않는다. 팀 공통 API 프록시는 VITE_BACKEND_URL을 사용하고 AI_API_TARGET을 설정하면 우선한다.
