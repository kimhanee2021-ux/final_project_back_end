# 파이널 게시판 등록·상세 조회 API

기준: 파이널 백엔드 develop 커밋 1d97114. 기존 JWT 필터와 SecurityConfig, BoardPost의 User 연관관계를 사용한다.
프런트엔드 변경은 포함하지 않는다.

## API

두 엔드포인트 모두 기존 정책대로 Authorization: Bearer <JWT>가 필요하다.

- POST /api/board: 201 Created, {"id":123}, Location: /api/board/123.
- GET /api/board/{id}: 상세 응답 DTO, 없는 글은 404.

등록 요청 예시:

```json
{
  "category": "팀에서_정한_카테고리",
  "appId": null,
  "tag": null,
  "title": "게시글 제목",
  "content": "게시글 내용",
  "isAnonymous": false,
  "expiresAt": null
}
```

category는 공백 불가·30자 이하, title은 공백 불가·255자 이하, content는 공백 불가, tag는 50자 이하이다.
isAnonymous는 필수다. 작성자 ID는 요청 본문에서 받지 않고 검증된 JWT로만 결정하며, 실제 User 존재를 확인한다.
익명 글은 상세 응답의 authorId를 null로 반환한다. Entity를 직접 노출하지 않는다.
입력 검증 실패는 400과 필드별 errors, 인증 실패는 기존 보안 필터의 401, 없는 글과 없는 작성자는 404다.

## 책임과 확장

- Controller: HTTP 요청·응답과 입력 검증.
- BoardPostService 인터페이스/구현: 사용자 확인, 저장·조회, 트랜잭션, DTO 반환.
- BoardPostRepository: JPA 저장소 접근.
- DTO: 외부 요청·응답 계약.
- BusinessException과 기존 GlobalExceptionHandler: 업무 오류 응답. 게시판 전용 ValidationHandler: 필드 검증 응답.

기존 BoardPost Entity와 User의 연관관계, 로그인 코드, 운영 DB 설정은 변경하지 않는다.

## 테스트

테스트용 application.yaml과 H2 MySQL mode를 사용한다. 실제 팀 MySQL 또는 Steam API에 접근하지 않는다.
회원가입·로그인 → 실제 서명 JWT → 등록 API → JPA 저장 → clear 후 조회를 검증한다.
별도 테스트는 테스트 트랜잭션 없이 등록 요청의 커밋, 독립 JDBC 조회와 별도 GET 요청을 확인한다.
인증 없음·잘못된 JWT·없는 사용자·없는 글·입력 오류·작성자 조작 방지·익명 응답을 검증한다.

## 남은 범위

- 실제 팀 MySQL에서의 스키마 호환성과 저장·조회 검증. H2는 이를 대신하지 않는다.
- 팀 카테고리 코드 허용 목록과 공지 등 관리자 카테고리 권한 규칙. 현재는 문자열 형식과 길이만 검증한다.
- 목록·검색·페이징·수정·삭제·댓글·추천·이미지 업로드.

## 작업 위치

D:\git\final_project_back_end의 원본을 수정하지 않고 해당 develop을 복제한 codex/board-api에서 작업했다.
GitHub 원격 주소는 원본과 같으나 업로드 성공 여부는 별도로 확인해야 한다.
