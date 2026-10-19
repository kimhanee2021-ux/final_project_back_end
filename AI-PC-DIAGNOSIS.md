# PC 진단 실제 연결 · 2026-10-19

## 구현
- 시스템 정보 사진(JPG/PNG/WEBP, 10MB·2400만 화소 이하) → 로그인/전송 동의 → Gemini 추출 → 사용자 확인·수정 → 화면 적용.
- CPU/GPU 모델, RAM 용량·속도, 총 저장·남은 공간. 미확인 숫자는 null. 사진과 PC 사양은 서비스 DB나 브라우저 저장소에 저장하지 않는다.
- 공식 Steam 검색/요구 사양을 사용. 요구 사양 원문을 정리한 내용·게임ID·조회 시각을 DB에 축적하며 24시간 캐시.
- 사용자 승인대로 O/X만 사용. A~D와 FPS 숫자는 미제공. 직접 모델 일치·OS·RAM·여유 공간이 확인되고 별도 미검증 API 요구가 없을 때만 O. X는 평가 불가이며 부족 확인과 비교 근거 부족을 구분.
- 로그인한 실제 Steam 보유 목록의 플레이시간 상위5개 자동 순차 평가. 펼치기10개/페이지·접기5개. 기록 없으면 이름순이라고 표시. 취소/사양 교체/로그아웃 이후 늦은 결과 폐기.
- 추천 업그레이드: CPU/GPU/RAM/SSD/메인보드/파워/케이스/쿨러 8분류·9개 독립 비교 후보. 실제 공개 판매가·조회 시각·출처·호환 확인 항목 표시. 하나의 조립 구성이나 호환 보장 추천이 아니다.
- 다나와 일반 최저가 표기만 수집. 현금·카드·쿠폰·적립금 제외. 판매점0/가격0은 판매가 없음. 상품명/용량/변형이 정확히 일치하지 않으면 미확인. 6시간 내 저장값 사용, 이후 조회 시 새 이력. 별도 예약 수집은 아직 없음.
- 이전 수집가 대비 하락률만 계산하며 공식 할인율/특가로 부르지 않는다. 확인된 정가·행사 근거 없는 특가 문구를 만들지 않는다.

## 실제 확인
- 비식별 검증용 시스템 정보 사진을 직접 작성해 업로드. 실제 Gemini가 i7-13700, UHD770, RAM32GB/3200MT/s, 총1.38TB와 사용207GB를 읽음. 여유 공간 계산과 내장 그래픽 표시 메모리 주의 사항 출력. 실제 사용자 장치ID가 든 사진은 외부로 전송하지 않음.
- 브라우저에서 추출 사양 적용 → Portal2 공식 검색 → 실제 Gemini 설명·X 평가·FHD/QHD/4K 조건부 설명·공식 출처 확인.
- 실제 가격 수집: 9개 중8개 OBSERVED, RTX3050LP는 판매가 없음. 가격은 시점별 변동값이며 구매 가격을 보장하지 않음.
- 로컬 MySQL에 새 pc_requirement_snapshots, pc_part_price_snapshots만 추가. 기존 스키마/사용자 자료 변경 없음.
- 브라우저 Steam 미연동 안내 확인. 실제 연동 계정이 없어 보유 목록→5개 전체 실사용 흐름은 미검증. 동일 API의 5개 연속 평가는 인증된 통합 테스트로 검증.
- 모바일390 설정 시 문서 너비/가로폭380/380, 가로 넘침 없음. 공통 헤더 보존. PC 경로의 잘못된 ‘모두 더미’ 푸터만 실제 설명으로 정정.

## 검증
- backend 전체153, 실패/오류0.
- frontend 전체48, ESLint/build 통과. 기존500KB 청크 경고 남음.
- 별도 읽기 검토에서 지연 보유 동기화 후 이전 사양 사용 가능성 발견 → specsRef/기대 사양 일치 검사로 수정 → 재검토 해결 확인.
- 본 기능은 Gemini를 새로 학습시키는 작업이 아니라 공식 요구 사양/가격 이력을 축적해 코드 비교와 AI 해설에 제공하는 방식.

## 근거 및 제한
- 게임: Steam 공식 appdetails/storesearch, 원문 링크는 각 결과에 표시.
- CPU/RAM 세대·BIOS: https://www.intel.com/content/www/us/en/support/articles/000092149/processors.html
- GPU 설치 확인 일반 안내: https://www.amd.com/en/resources/support-articles/faqs/GPU-Install.html
- RTX5060LP 제조사: https://www.zotac.com/us/product/graphics_card/zotac-gaming-geforce-rtx-5060-low-profile
- SSD 데이터시트: https://download.semiconductor.samsung.com/resources/data-sheet/samsung_nvme_ssd_990_evo_plus_datasheet_rev.1.0_10139908982130.pdf
- DDR5 보드: https://www.msi.com/Motherboard/PRO-B760M-A-WIFI/Specification
- 파워 케이블 일반 안내: https://knowledge.seasonic.com/article/68-cable-compatibility
- 케이스/쿨러 간섭 일반 안내: https://www.noctua.at/en/compatibility
- 일반 안내 자료를 다른 제조사 제품의 호환 인증으로 취급하지 않으며 UI에도 구분.
- 특정 사용자의 보드·PSU·OEM 케이스/커넥터·슬롯 정보가 없으므로 장착 가능성·구매 필요·성능 향상을 확정하지 않는다.
- 실측 벤치마크 자료 수집/연동은 미완료. FHD/QHD/4K 정량 FPS, A~D, 실제 특가 행사 검증은 제공하지 않는다.

## 병합 기준
기존 AI PR(front#4/back#3)에 의존하므로 PC 변경 PR은 codex/ai-team-structure를 기준으로 작성. 기존 AI 전체를 develop에 다시 포함하는 PR로 만들지 않는다. 팀장이 기존 PR 병합 후 PC PR의 기준을 develop로 변경하여 병합한다.


## API·팀 실행
- POST /api/ai/pc/extract: 인증 multipart(image). 성공 {specs,warnings}.
- POST /api/ai/pc/assess: 인증 {appId,specs}. 성공 {appId,gameName,grade,checks,summary,resolutions,settings,upgradeChecks,limitation,requirements}.
- GET /api/ai/pc/games?keyword=: 인증 Steam 공식 검색(최대10).
- GET /api/ai/pc/parts: 인증 8분류9후보·가격/이력/출처.
- DB: docs/db/pc_requirements.sql, docs/db/pc_part_prices.sql. 테이블이 없으면 실행 전 적용. 기존 데이터 삭제 없음.
- 서버 설정은 기존 GEMINI_API_KEY/GEMINI_MODEL/DB/JWT 사용. 별도 키나 클라이언트 키 없음.
- 기존 AI 공통 6회/분·동시1 한도 공유. 보유게임5개는 순차 처리, 실패/429시 완료 결과 보존 후 중단·수동 재시도.
- 실제 사진/실제 회원 정보는 테스트 fixture나 제품 소스에 포함하지 않음.
