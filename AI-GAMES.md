# AI 게임 추천·구매 상담

별도 브랜치 `codex/ai-games`. 기존 팀 `develop` 기반이며 공지사항 변경을 포함하지 않는다.

## 실행과 연결

- `POST /api/ai/screenshots`: 인증된 multipart 요청, `image` 파일, `type=item|play|error`, 선택 `game` (100자). JPG/PNG/WEBP 10MB·2400만 화소 검사. 응답 `{summary,details,actions,uncertainty,isMock:false}`. 기존 AI 분당6회/동시1회 제한 공유. 이미지 API 제한 90초. 별도 DB 스키마 변경 없음.
- 이미지·답변은 앱의 DB나 영구 파일에 저장하지 않는다. 프레임워크의 multipart 임시 파일은 요청 종료 시 정리된다. ImageIO 메타데이터는 메모리 캐시를 사용한다. 키/이미지/업스트림 원문은 로그에 출력하지 않는다.
- 전송 방식은 [Google 공식 이미지 이해 문서](https://ai.google.dev/gemini-api/docs/image-understanding)의 inline 이미지 입력을 따른다. 이미지 속 지시문은 분석할 자료로 처리하도록 시스템 규칙을 둔다. 읽히지 않는 수치와 오류 원인을 확정하지 않도록 요구하고 JSON 응답을 검증한다.

- 서버 환경에 `GEMINI_API_KEY`를 설정한다. 선택 설정 `GEMINI_MODEL`의 기본값은 `gemini-flash-latest`. 키는 프런트나 Git에 넣지 않는다.
- 기존 DB/JWT 설정이 필요하다. 기존 `games` 테이블의 데이터로 추천한다. 빈 카탈로그에서는 게임 데이터 등록 안내를 반환한다.
- 기존 로그인 JWT를 Bearer 헤더로 전달한다.
- `POST /api/ai/chat`: `{mode:"recommend"|"purchase",message,genre?,gameId?,history?:[{role:"user"|"model",text}]}`. 구매 상담은 gameId 필수.
- `GET /api/ai/games?keyword=...`: 구매 상담 대상 검색, 최대 10개.
- 응답 `{answer,games:[{appId,name,genre,priceInfo,thumbnailUrl,storeUrl,lastSyncedAt,reason}]}`. 카드의 정보와 링크는 DB에서 구성한다.
- 질문 2000자, 대화 6개, 후보 30개, 추천 3개. 회원별 분당 6회·동시 1회 제한은 단일 서버 메모리 기준이다. 여러 서버 운영 시 공용 제한 저장소로 연결해야 한다.
- 외부 응답 제한 30초. 자동 재시도 없음. 키 미설정·한도·잘못된 응답을 안전한 오류로 반환한다. 대화는 서버에 저장하지 않는다.

## 검증 (2026-10-10)

- AI 통합 테스트 12개 통과: 인증/입력/검색/게임 선택/응답과 등록 ID 검증/외부 실패.
- 실제 Gemini 게임 추천·구매 상담 API 및 브라우저 확인.
- 기존 전체 테스트 69개 중 게시판 테스트 9개 실패: BoardApiDocs/BoardController 등의 검증 어노테이션 불일치 `HV000151`. AI 외 기존 영역이며 전체 테스트 통과로 표현하지 않는다.
- 로컬 검증 DB에 Steam 공식 정보로 게임 8개를 적재했다. 이 데이터는 Git에 포함하지 않는다. 팀 DB의 games 데이터 연결이 필요하다.

AI 문장은 오류가 있을 수 있으며 가격·할인·플레이 인원은 판매처에서 확인한다. PC 사양/라이브러리/스크린샷 분석 기능은 이번 범위에 없다.
