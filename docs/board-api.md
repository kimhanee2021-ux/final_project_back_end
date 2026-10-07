# 파이널 게시판 백엔드 API

기존 파이널 JWT 인증과 BoardPost/User 모델을 사용한다. 모든 API는 Authorization: Bearer <JWT>가 필요하다. Controller는 HTTP, Service는 권한·업무·트랜잭션, Repository는 DB 접근, DTO는 외부 계약을 담당한다.

## 게시글

- POST /api/board: 등록. 201과 {"id":123}, Location 반환.
- GET /api/board/{id}: 상세. 없는 글은 404.
- GET /api/board?page=0&size=20&category=FREE&keyword=steam: 최신 작성일·ID 내림차순 목록.
- PUT /api/board/{id}: 본인 글 전체 수정, 200과 상세 DTO.
- DELETE /api/board/{id}: 본인 글 삭제, 204. 댓글 또는 이미지가 연결되면 409. 해당 글의 BOARD_POST 상호작용만 정리한다.

등록·수정 필드: category(필수·30자), appId, tag(50자), title(필수·255자), content(필수), isAnonymous(필수), expiresAt. 작성자는 JWT에서 결정하며 작성일·조회수·대표 이미지는 해당 요청으로 바꾸지 않는다.

목록은 page 0부터, size 기본20·최대100. category 최대30자, keyword 최대200자. 공백 필터는 무시한다. 검색어는 제목 또는 본문에 대소문자 구분 없이 부분 일치하며 %, _, 역슬래시는 문자 그대로 검색한다. 카테고리와 검색은 함께 적용된다.

페이지 응답: content, page, size, totalElements, totalPages, first, last. 목록 항목은 본문을 제외한다. 익명 글의 authorId는 null이다.

## 댓글·대댓글

- POST /api/board/{postId}/comments: {"content":"댓글","parentId":null}, 201과 DTO·Location.
- GET /api/board/{postId}/comments?page=0&size=20: 작성일·ID 오름차순 페이지 목록, size 최대100.
- PUT /api/board/{postId}/comments/{commentId}: {"content":"수정"}, 본인만 수정, 200과 DTO.
- DELETE /api/board/{postId}/comments/{commentId}: 본인만 삭제, 204. 답글이 있으면 409.

본문은 필수·최대2000자. parentId를 생략하면 일반 댓글이다. 같은 게시글의 최상위 댓글에만 답글을 달 수 있다. 없는 부모·다른 글 부모는 404, 대댓글을 부모로 지정하거나 0 이하 ID는 400이다. 수정으로 부모를 변경할 수 없다.

댓글 DTO: id, postId, parentId, content, authorId, authorNickname, accepted, createdAt.

## 추천

- PUT /api/board/{postId}/likes: 내 추천 등록. 중복 요청은 그대로 유지한다.
- DELETE /api/board/{postId}/likes: 내 추천 취소. 이미 취소한 요청도 동일하게 처리한다.
- GET /api/board/{postId}/likes: 추천 수와 내 추천 여부.

응답은 200과 {"postId":123,"likeCount":1,"liked":true}. 본인 글 추천도 허용한다. ContentInteraction 대상 코드는 BOARD_POST, 동작 코드는 LIKE다. 추천 변경과 글 삭제는 게시글 행 잠금을 사용한다.

## 이미지·대표 이미지 선택

- POST /api/board/{postId}/images: multipart/form-data의 file 필드, 한 번에 한 장. 작성자만 가능. 201과 {"id":1,"url":"/api/board/.../images/files/<uuid>.png","sortOrder":0}.
- GET /api/board/{postId}/images: 업로드 순서의 이미지 메타데이터 목록.
- GET /api/board/{postId}/images/files/{key}: JWT 인증 후 원본 이미지 조회.
- PUT /api/board/{postId}/images/{imageId}/cover: 작성자가 대표 이미지를 선택한다. 200과 선택된 이미지 DTO.
- DELETE /api/board/{postId}/images/{imageId}: 작성자만 삭제, 204.

게임 스크린샷을 고려해 JPG·PNG 파일당20MiB, 최대4천만 화소, 글당10장까지 허용한다. 이미지 헤더와 실제 디코딩을 검사한다. 원본 바이트를 유지하며 리사이즈·재압축하지 않는다. 4K·8K 해상도도 화소수와 파일 크기 제한 안에서 허용한다.

첫 업로드는 기본 대표가 된다. 사용자가 원하는 이미지로 언제든 변경할 수 있다. 다른 이미지를 삭제해도 대표는 유지하며, 대표를 삭제하면 남은 첫 이미지로 바뀐다. 모두 삭제하면 대표는 null이다. 업로드 한도 초과는 409, 잘못된 파일은 400이다. 실제 HTTP의 multipart 용량 초과는 프레임워크가 413으로 처리할 수 있다.

파일 이름은 원본 이름 대신 UUID를 사용한다. DB 트랜잭션 롤백 시 신규 파일을 정리하고, 이미지 삭제 커밋 후 파일을 정리한다. 파일 정리 실패는 로그에 기록한다. DB와 파일 시스템 간 장애 복구를 위한 영속 작업 큐는 이번 범위에 포함하지 않는다.

저장은 BoardImageStorage 인터페이스로 분리했다. 현재 구현은 로컬 폴더다. BOARD_IMAGE_DIRECTORY 환경 변수로 경로를 지정하고 기본값은 ./uploads/board이다. 배포에서는 이 경로를 영속 볼륨에 연결해야 한다. 여러 서버에서 공유하는 저장소나 S3 연결은 별도 설정·구현이 필요하다.

이미지 조회도 JWT 헤더가 필요하므로 프런트엔드에서는 인증된 fetch 후 Blob URL로 표시할 수 있다. URL에 토큰을 넣지 않는다. 프런트엔드 변경은 포함하지 않았다.

## 검증과 적용 상태

전체 자동 테스트 57개 통과(H2). 이미지 테스트는 임시 폴더에서 실제 파일 저장·조회·삭제·롤백 정리·4K 원본 유지·대표 선택과 삭제 후 대체를 확인했다. 정적 리뷰에서도 차단 문제는 발견되지 않았다.

기존 게시글 등록·상세는 로컬 MySQL에서 검증했다. 이후 기능의 실제 MySQL 및 추천 동시 요청 검증은 포함하지 않았다.

원본 D:\git\final_project_back_end의 추천 커밋 7749d3f까지 적용을 확인했다. 이미지 변경은 별도 패치로 제공한다. GitHub 업로드는 하지 않았다.

## 남은 범위

팀 카테고리 코드·관리자 권한 규칙, 댓글 채택·댓글 추천·신고 연계, 프런트엔드 연결과 실제 배포 저장소 설정.
