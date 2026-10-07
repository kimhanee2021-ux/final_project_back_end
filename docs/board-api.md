# 파이널 게시판 API

기존 파이널 JWT 인증과 BoardPost/User 연관관계를 사용한다. 모든 API는 Authorization: Bearer <JWT>가 필요하다.

## 제공 기능

- POST /api/board: 게시글 등록. 201, {"id":123}, Location 헤더 반환.
- GET /api/board/{id}: 상세 조회. 없는 글은 404.
- GET /api/board: 목록·페이지 나누기·카테고리 필터·제목/내용 검색.

목록 예: /api/board?page=0&size=20&category=FREE&keyword=steam

page는 0부터 시작한다. 기본 size는 20이며 1~100을 허용한다. category는 최대 30자, keyword는 최대 200자다. 앞뒤 공백은 제거하며 빈 값은 필터를 적용하지 않는다. 검색은 대소문자를 구분하지 않는 부분 일치이며 %, _, 역슬래시도 문자 그대로 검색한다. 카테고리와 검색어는 함께 적용된다.

최신 작성일, ID 내림차순으로 정렬한다. 응답은 content, page, size, totalElements, totalPages, first, last를 포함한다. 목록 항목은 본문을 제외한 요약이며 익명 글의 authorId는 null이다.

등록 요청은 category, appId, tag, title, content, isAnonymous, expiresAt을 받는다. category는 필수·30자 이하, title은 필수·255자 이하, content와 isAnonymous는 필수, tag는 50자 이하다. 작성자는 JWT로 결정한다.

## 구조와 검증

Controller는 HTTP와 입력 검증, Service는 업무 처리와 트랜잭션, Repository와 Specification은 저장·조회 조건, DTO는 응답을 담당한다.

전체 자동 테스트 24개 통과: H2 기반 등록·상세·실제 JWT·독립 커밋 조회와 목록·복합 검색·문자 검색·페이지·잘못된 입력·익명 응답을 검증했다. 기존 등록·상세는 로컬 MySQL에서도 별도로 검증했다. 목록 변경분의 실제 MySQL 검증은 포함하지 않았다.

## 남은 기능

수정·삭제·댓글·추천·이미지 업로드와 팀 카테고리 허용 목록·관리자 권한 규칙.

## 적용 상태

목록 변경은 원본의 dfdc731을 기준으로 codex/board-list에서 작성했다. 원본 D:\git\final_project_back_end 적용용 Git 패치를 제공하며 GitHub에는 업로드하지 않았다.

## 수정·삭제 추가

- PUT /api/board/{id}: 등록과 같은 필드의 전체 교체 요청. 성공 시 200과 수정된 상세 응답. 작성자·작성일·이미지·조회수는 요청으로 변경하지 않는다.
- DELETE /api/board/{id}: 작성자만 삭제 가능. 성공 시 204. 없는 글은 404, 다른 작성자는 403, 인증 없음은 401.
- 댓글 또는 이미지가 연결된 글은 409로 삭제를 거절한다. 연관 데이터의 삭제 정책과 추천·신고 대상 코드 정리는 해당 기능 구현 시 확정한다.

수정·삭제 통합 테스트 8개를 추가했고 전체 32개가 통과했다. 수정·삭제 변경의 실제 MySQL 검증은 포함하지 않았다.
원본 목록 커밋 bd6e29e 적용을 확인했으며 이 추가 변경은 별도 패치로 적용한다.

## 댓글 API

기존 JWT 인증이 모든 댓글 API에 필요하다.

- POST /api/board/{postId}/comments: {"content":"댓글"}. 201과 생성된 댓글 DTO·Location 반환. 작성자는 JWT로 결정한다.
- GET /api/board/{postId}/comments?page=0&size=20: 작성일·ID 오름차순 페이지 목록. size 1~100. 해당 글이 없으면 404.
- PUT /api/board/{postId}/comments/{commentId}: {"content":"수정"}. 본인 댓글만 수정, 성공 200과 DTO.
- DELETE /api/board/{postId}/comments/{commentId}: 본인 댓글만 삭제, 성공 204.

본문은 공백 불가·최대 2000자다. DTO는 id, postId, parentId, content, authorId, authorNickname, accepted, createdAt을 포함한다. 페이지 응답은 게시글 목록과 동일한 메타데이터 구조다.

비작성자는 403, 없는 댓글 또는 다른 글에 속한 댓글은 404다. 답글이 연결된 댓글은 409로 삭제를 거절한다. 대댓글 등록·채택·추천·신고 연계는 이번 범위에 포함하지 않는다.

원본 수정·삭제 커밋 32e3fa3 적용을 확인했다. 댓글 변경은 별도 패치이며 실제 MySQL에서의 댓글 검증은 포함하지 않았다.
댓글 통합 테스트 8개 추가 후 전체 자동 테스트 40개 통과(H2). 코드 정적 리뷰에서 차단 문제 없음.
