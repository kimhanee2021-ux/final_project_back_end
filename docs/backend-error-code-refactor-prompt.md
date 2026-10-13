# 백엔드 에러 응답 code 필드 추가 — 실행 프롬프트

- 작성일: 2026-10-10
- 대상 저장소: `final_project_back_end`
- 배경: 프론트엔드가 백엔드 에러 응답의 `error` 문자열 메시지를 그대로 비교해서 분기하고 있음
  (`AuthPage.jsx`의 `.includes('이메일')` 등, `useSteamLibrary.js`의 `STEAM_NOT_LINKED_MESSAGE` 정확히 일치,
  `apiClient.js`의 `'로그인이 필요합니다'` 정확히 일치). 백엔드가 문구를 바꾸면 프론트가 조용히 깨지는 구조라
  `ErrorResponse`에 안정적인 `code` 필드를 추가하기로 함.

아래 블록을 그대로 백엔드 프로젝트의 Claude Code 세션에 붙여넣고 실행한다.

## 실행 프롬프트

```
final_project_back_end 저장소에서 아래 작업을 진행해줘.

## 배경
프론트엔드(final_project_front_end)가 지금 백엔드 에러 응답의 `error` 문자열 메시지를
그대로 비교해서 분기하고 있다 (예: "이미 가입된 이메일입니다" 포함 여부로 입력칸 결정,
"스팀 계정이 연동되어 있지 않습니다" 정확히 일치로 "연동 안 됨" 상태 판단,
"로그인이 필요합니다" 정확히 일치로 자동 로그아웃 여부 판단).
지금처럼 사람이 읽는 문구만 내려주면 백엔드가 그 문구를 한 글자라도 바꿀 때마다
프론트가 조용히 깨진다. `ErrorResponse`에 안정적인 `code` 필드를 추가해서
프론트가 문자열 대신 code로만 분기할 수 있게 만들어줘.

## 작업 1: ErrorResponse에 code 추가
- `global/exception/ErrorResponse.java`를 `record ErrorResponse(String code, String error)`로 변경
- `BusinessException`이 `status`, `code`, `message`를 함께 들고 다니도록 수정
- 아래 서브클래스는 고정 code를 부여:
  - DuplicateEmailException → DUPLICATE_EMAIL
  - DuplicateNicknameException → DUPLICATE_NICKNAME
  - DuplicateSteamIdException → DUPLICATE_STEAM_ID
  - SteamNotLinkedException → STEAM_NOT_LINKED
  - InvalidSteamAuthException → INVALID_STEAM_AUTH
  - PrivateSteamLibraryException → PRIVATE_STEAM_LIBRARY
  - SteamApiException → STEAM_API_ERROR
  - UserNotFoundException → USER_NOT_FOUND
  - InvalidRequestException → INVALID_REQUEST

- `InvalidCredentialsException`은 지금 생성자가 메시지만 받는데, 실제로는 의미가 다른
  두 가지 상황에 같이 쓰이고 있다:
  1. 진짜 로그인 실패 — AuthServiceImpl("가입되지 않은 이메일입니다", "비밀번호가
     일치하지 않습니다"), SteamAuthServiceImpl("유효하지 않거나 만료된 토큰입니다"),
     UserServiceImpl("현재 비밀번호가 일치하지 않습니다") → code: INVALID_CREDENTIALS
  2. BoardCommentServiceImpl, BoardImageServiceImpl, BoardLikeServiceImpl,
     BoardPostServiceImpl에서 `userId == null`일 때 던지는 "로그인이 필요합니다."
     → 이건 '자격증명이 틀림'이 아니라 '로그인 자체가 안 됨'이라 의미가 다르다.
     새 예외(`AuthenticationRequiredException`, status 401, code: UNAUTHENTICATED)를
     만들어서 이 4곳의 `InvalidCredentialsException("로그인이 필요합니다.")` 호출을
     교체해줘.
  → `InvalidCredentialsException`은 code를 생성자 파라미터로 받도록 바꿔서
    AuthServiceImpl/SteamAuthServiceImpl/UserServiceImpl 호출부가 INVALID_CREDENTIALS를
    명시하게 해줘.

- `ConstraintViolationException` 핸들러 → code: VALIDATION_ERROR
- `GlobalExceptionHandler`의 모든 분기가 `code`를 함께 응답하도록 수정

## 작업 2: NullPointerException을 인증 신호로 쓰지 않기
- 지금 `GlobalExceptionHandler.handleNullPointer`가 아무 NullPointerException이나 잡아서
  401 + "로그인이 필요합니다"로 응답한다. 이러면 실제 널 참조 버그와 "로그인 안 함"을
  구분할 수 없고, 프론트의 401 자동 로그아웃 판단도 같이 오염된다.
- 이 핸들러는 제거하거나, 최소한 401이 아니라 500으로 바꾸고 로그를 남기도록 수정해줘.
  "로그인 필요" 응답은 위에서 만든 `AuthenticationRequiredException`과, 이미 정상
  동작하는 Spring Security의 401(토큰 없음/무효 시, 본문 없음)만으로 충분한지 확인해줘.
- 이 핸들러가 왜 추가됐는지(의도한 실제 호출 경로가 있는지) 먼저 확인하고,
  제거/변경 시 영향받는 곳이 있으면 작업 전에 알려줘.

## 완료 기준
- `./gradlew build` 통과
- Swagger 문서(`AuthApiDocs`, `UserApiDocs`, `LibraryApiDocs`)에 에러 응답 예시가
  있다면 `code` 필드를 추가해서 갱신
- 각 에러 상황의 응답이 `{ "code": "...", "error": "..." }` 형태로 나오는지 확인
  (가능하면 테스트로 검증)
- 기존 `error` 문구는 바꾸지 말 것 — 화면에 그대로 노출되는 사용자용 문구라 유지해야 함
- 작업이 끝나면 "상황 → code" 표를 정리해서 알려줘. 프론트에서 그 표를 보고
  문자열 매칭을 code 매칭으로 바꿀 거다.

## 하지 않아도 되는 것
- final_project_front_end 쪽 코드는 건드리지 마. code가 내려오면 프론트는 별도로 처리한다.
```

## 프론트엔드 후속 작업 (백엔드 완료 후)

백엔드가 `code`를 내려주기 시작하면 아래 3곳을 문자열 매칭에서 code 매칭으로 바꾼다.

| 파일 | 현재 방식 |
|---|---|
| `src/pages/auth/AuthPage.jsx` | `text.includes('이메일')` 등 |
| `src/features/library/api.js` + `src/features/library/hooks/useSteamLibrary.js` | `message === STEAM_NOT_LINKED_MESSAGE` |
| `src/utils/apiClient.js` (`isSessionExpired`) | `message === '로그인이 필요합니다'` |
