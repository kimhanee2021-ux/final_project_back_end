# 공지사항 팀 구조 정리 결과

2026-10-13. 팀 최신 develop 프런트 0bda262 / 백엔드 4cf1121을 로컬 작업 브랜치 codex/notice-team-structure에 통합했다. 팀 develop에 직접 병합하거나 원격으로 업로드하지 않았다.

## 구조와 연결
- 프런트 pages/notices는 화면 진입점만 유지한다. features/notices 아래 api, components, hooks, mocks와 입력/권한 모델을 분리하고 스타일은 styles/notices.css에 둔다.
- 별도 noticeToken과 공지 전용 로그인은 제거했다. 팀 AuthProvider와 utils/apiClient를 사용하며 /login?next=/notices로 돌아온다.
- 백엔드 domain/notice의 controller/service/repository/entity/dto/exception 계층을 유지한다. 최신 공통 인증 예외와 BusinessException(status,code,message)을 연결했다.
- 권한·없는 공지·본문 입력·페이지 매개변수 오류에 NOTICE_FORBIDDEN, NOTICE_NOT_FOUND, VALIDATION_ERROR를 제공한다.
- 회원 작성, 작성자만 수정·삭제 정책을 유지한다. 관리자/등급 정책은 아직 없다.
- 개발 전용 /notices?preview=1은 샘플 12개 읽기 전용이다. 서버 데이터와 분리하며 작성·수정·삭제하지 않는다.

## 검증
- 프런트 테스트 13개, ESLint, production build 통과. 기존 500KB 청크 경고가 있다.
- 백엔드 전체 테스트 86개, 실패/오류 0. 페이지 음수/크기 0/최대 초과/문자 입력의 400 오류 코드도 검증했다.
- 실제 공통 로그인 후 브라우저 작성·수정·목록·상세 확인. 다른 회원 상세 화면에 수정·삭제 버튼이 없음을 확인했다.
- 실제 API 다른 회원 수정·삭제 403, 없는 공지 404, 입력 오류 400, 비회원 401 확인. 작성자 삭제 204 후 조회 404 확인.
- 모바일 390px에서 가로 넘침 없음. 실제 검증용 공지 1개와 임시 회원 2명 삭제, 재로그인 401 확인. 인증 임시 파일도 삭제했다.
- 읽기 전용 독립 검토에서 최종 차단 문제 없음. 공지 전용 DB 구조 변경 없음. 실제 .env와 키는 커밋하지 않는다.

공지사항은 AI/테트리스 구조 정리 브랜치와 별도이다. 팀장이 병합할 때 각각의 최신 develop 기반 변경을 검토한다. 원격 업로드와 팀 병합은 아직 진행하지 않았다.
