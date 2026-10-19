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

## 실제 Steam 연동 후속 검증
- 실제 사용자 계정의 보유49개/실제 플레이시간 정렬, 기본5개·펼치기10개·다음페이지·접기5개 브라우저 확인.
- Overwatch(app2357570)는 KR appdetails HTTP200/success:false, US는 성공. HTTP200의 지역 메타데이터 누락만 미국 공식 정보로 보완하고, 접근 거절 HTTP403 등은 우회하지 않음. 동일 steam_appid 검증.
- source_url을 DB에 저장하여 사용 지역이 포함된 공식 출처 보존. 기존 테이블은 docs/db/pc_requirements_source_upgrade.sql 한 번 적용, 새 설치는 pc_requirements.sql만 적용.
- 지역 누락·양쪽 자료없음·접근 거절·잘못된 응답 테스트4개 추가. success:false가 명시된 경우에만 지역 메타데이터 보완.
- 실제 보유 게임 평가 중 Gemini 지연으로 타임아웃 발생. 완료 결과는 보존되며 실패 게임은 개별 재시도 가능. 실패를 실제 평가 성공으로 표현하지 않음.
- 공식 자료/규칙 비교 후 Gemini 해설 502/503/504/외부429 또는 응답 검증 실패 시 explanationStatus=UNAVAILABLE로 공식 비교를 반환. 인증/로컬 제한/공식 자료 실패는 정상 결과로 변환하지 않음.
- 실제 추가3게임 공식 비교 확인, 당시 Gemini 해설은 UNAVAILABLE. 해설 실패 원인은 미확정이며 정상 AI 해설로 표시하지 않음. 임시 검증 회원 삭제204 확인.
- 백엔드 전체159테스트 실패/오류0. 실측FPS/A~D 및 부품 개별 장착 호환 확정은 지원하지 않음.

## 외부 AI 한도 확인 및 요청 절약
- 실제 서버에서 Overwatch/Hollow Knight/Elden Ring 해설 READY를 각각 확인한 뒤, 추가 5개 연속 검증에서는 PUBG READY 후 나머지 요청이 공급자429로 제한됨. 전체5개 연속 AI 해설 성공은 아직 미검증.
- 공식 오류: RESOURCE_EXHAUSTED, generate_content_free_tier_requests, limit20, gemini-3.8-flash(현재 gemini-flash-latest 별칭 응답). 당시 재시도 안내 약19시간. 전체 모델/모든 계정 공통 한도라는 뜻은 아님.
- RATE_LIMITED 상태는 공식 비교를 유지하지만 이후 자동 게임 요청은 중단. 완료 결과/현재 게임 출처 보존. UNAVAILABLE 일반 장애와 구분. UI에 이용 한도 표시.
- 서버에는 HTTP상태/잘림 여부만 기록. 응답본문/키/사진/개인 사양은 로그에 남기지 않음.
- 로컬 테스트는 외부 AI 호출 없이 검증. 프런트51테스트 및 lint/build PASS. 유료 전환/결제/다른 키로 우회하지 않음.
- 공식 한도 문서: https://ai.google.dev/gemini-api/docs/rate-limits (프로젝트/모델별 한도 및 일일 초기화 안내).

## AI 미호출 기본 비교
- includeAi:false 요청은 Gemini/공통AI한도에 접근하지 않고 공식 요구 사양 및 코드 비교 결과 NOT_REQUESTED 반환. 생략/true는 기존 AI요청 유지.
- 보유상위5 기본 공식비교. 보유/검색 모두 공식비교와 AI해설 버튼 분리. 별도 검토에서 검색게임 AI버튼 누락 발견 후 수정.
- 사진 없이 사양 직접입력 가능. CPU/GPU/RAM 및 SSD총량/여유공간 확인. 저장/AI전송 없이 메모리에 유지하며 게임 비교는 기존 회원인증 필요.
- 실제 API5게임 모두 NOT_REQUESTED 및 가격9개(관측8/판매가없음1) 확인. TTL6시간 내 가격은 기존수집값이며 최신 재수집으로 표현하지 않음. 임시회원 삭제204.
- front51테스트/lint/build PASS, backend전체161 실패오류0. 공식비교7연속 호출에도 Gemini호출없음 회귀검증.
- 무료대안 공식조사는 작업 outputs/무료-AI-대안-2026-10-19.md. Groq/OpenRouter 후보, 공급자 신규연결/키발급/개인사진전송은 미진행.

## 최종 방향 정정: AI 중심 자동 진단
- 사용자 정정으로 공식비교 기본/별도 AI해설 방식은 폐기. 사양 확인·적용 후 로그인한 실제 회원의 보유게임 동기화→상위5개 자동 AI분석. 게임 선택 진입점1개 유지, 검색게임도 AI분석.
- 등급은 Gemini가 O/X로 반환하고 판단 이유/해상도별 설명/설정/업그레이드 검토를 생성. 서버는 미검증 근거를 넘어 O로 올리는 응답, A~D, 숫자FPS 등은 거부. AI실패시 공식비교는 남겨도 AI평가완료로 표시하지 않음.
- 서버가 수집한 부품9후보/관측가격/시각/출처/호환확인조건을 AI에 전달. AI가 최대3개의 검토후보·추천이유·장착확인조건을 선택. 상품ID를 서버후보와 다시결합하여 가격/출처를 AI가 만들지 못하도록 검증.
- 판매가없음/0원/목록밖상품/중복추천 거부. 근거없는 교체 강요 대신 추천빈배열 허용. 전체시장 조사나 특정PC 장착호환 인증은 아님.
- UI는 AI선택후보만 추천영역에 표시. AI미완료는 대기/한도상태를 표시. 수동 전체부품조회 버튼으로 AI추천을 대신하지 않음.
- 백엔드전체169 실패오류0, 프런트51/lint/build PASS(기존청크경고). 후보검증4/AI등급검증3/API 실제수집가격결합1 추가검증. 독립읽기검토 큰문제없음.
- 새 grade/recommendations 응답형식의 실제 Gemini 성공 검증은 공급자무료일일한도소진으로 미완료. 이번단계는 로컬모의AI API검증이며 새추천실응답을 확인했다고 표현하지않음. 새공급자/결제/키변경없음.
- 사진/PC사양은영구저장안함. 최초가격수집+AI응답을 고려한 프런트분석상한210초, 취소와늦은응답폐기 유지.

## 2026-10-19 최신 상태: Groq PC 분석 및 제품 직접 조사
이 절이 앞선 단계별 기록보다 우선한다. 아래 과거 기록의 'Groq 미연결/AI 별도 버튼/9개 후보'는 현재 상태가 아니다.

- PC 전용 공급자 auto: GROQ_API_KEY가 있으면 Groq, 없으면 Gemini. 명시적 PC_AI_PROVIDER 설정 지원. 다른 AI 기능은 기존 Gemini 유지. 키 서버 전용, 실제 .env 미추적.
- Groq 모델 qwen/qwen3.8-27b: 사진/엄격 JSON 실제 연결. 실패 시 다른 공급자로 사진을 재전송하지 않는다. 사진 전송 전에 실제 공급자 표시 및 동의. 직접 입력도 확인 사양이 게임 AI에 전송됨을 고지하고 회원은 동의 후 적용.
- 사진은 저장공간 원문/단위를 읽고 서버가 총량-사용량을 계산한다. 원문 없으면 미확인, AI가 계산한 여유공간을 대신 채우지 않는다. 1.38TB/207GB fixture → 1413.12/1206.12GB 확인. OS/코어 등 사진에 없는 값은 빈값.
- Groq 출력 사진768/분석900토큰, 기본 요청 간격60초. 잠금 대기15초/공급자 대기60초/HTTP90초로 제한. 사진 UI210초, 게임300초. 429 자동재시도 없음, 남은 자동게임 분석 중단 및 완료 결과 유지. 무료 한도와 모델 가용성 보장 없음.
- O/X·해상도별 조건·추천은 AI 분석. 확인 근거보다 높은 O 및 일부 CPU/GPU 근거없는 충족 문구를 서버에서 거부한다. 문구 검사는 모든 의미 오류를 검출하는 장치가 아니다. 실측FPS/A~D 미지원.
- CPU/GPU/RAM/SSD/메인보드/파워/케이스/쿨러 8분류11개 조사 후보. WD SN7100 1TB·Thermalright AXP90-X47 그레이 추가. 관측10/판매가없음1, 실제 가격/시각/링크는 /parts 응답 기준. RX6400 LP 및 SN770은 조사 당시 가격비교 중지여서 추가하지 않음.
- 조사 후보 전체를 반드시 구매 추천하지 않는다. AI가 실제 필요와 호환 확인 조건으로 선택하며 Groq 무료 출력 예산상 한 응답의 추천은 최대1개로 요청한다. 전체 시장 탐색/최저가 보장/확정 호환 인증 아님.
- 일반 판매가와 카드/멤버십/적립 혜택 구분. 공식 할인 근거 없으면 특가나 할인율을 만들지 않음. 최초 수집가격은 참고가격이며 배송비/최종 결제가는 판매처 확인.
- 최신 자동 검증: backend 전체184 실패/오류0, frontend51/lint/build PASS(기존청크경고). 사진 실제 Groq 추출 성공, 실제 /parts11개 조회 및 임시회원 삭제204. 상세 live 결과 및 제품 출처는 작업 outputs/PC-업그레이드-제품조사-2026-10-19.md 참조.


- 제조사 근거 후속: i7-14700 일반 안내를 정확한 Intel 제품 사양표로 교체하고 LGA1700/기본65W·최대터보219W 장착 확인사항 보강. 다른 판매 플랫폼의 불명확한 이중가격·종료행사는 현재 특가로 등록하지 않음.

## 다른 판매처 자동 수집 — 구현 후 사용자 검증 진행 중
- COMTRIX 파워 동일 제품96986270 한 개부터 연결. 서버 고정 허용 URL만 요청, 임의 AI/사용자 URL이나 리다이렉트 접근 사용 없음. JSON-LD 정확한 모델·KRW·InStock·새상품·유효기간·메타가격 일치를 확인하며 실패 시 가격 숨김.
- 기존 공개 가격 테이블에 source_key 추가. DANAWA/COMTRIX 이력과6시간 캐시를 분리하여 판매처 간 차이를 할인율로 계산하지 않음. 새 설치는 pc_part_prices.sql, 기존 설치는 pc_part_sources_upgrade.sql 한 번 적용. 로컬은 컬럼/인덱스 추가 완료, 기존 이력 보존.
- AI에 전달하는 offer에 retailQuotes를 포함하고, AI 선택 제품 카드에서 관측된 다른 판매처 가격/출처/시각/주의사항 표시. 주 가격은 기존 다나와 값이며 다른 판매처 중 최저값을 주 가격으로 자동 대체하지 않음.
- 모든 부품/플랫폼 전수 수집은 아니며 초기 연결은 COMTRIX 파워1제품. 배송비/회원조건/최종 재고는 판매처 확인, 검증된 특가나 할인율 추가 없음.
- 공개 페이지 직접 조사 당시67800원 확인. 제품 데이터 원문/사진은 대량 복제하지 않음. 새 parser5테스트 작성, 이 변경의 테스트/빌드 통과 여부는 사용자 BAT3 결과 대기. 앞선184개 통과 기록을 새 변경 검증으로 사용하지 않는다.

### 후속 보완: 대체 가격의 실제 출처 유지
- 주 가격이 미확인이고 다른 판매처 가격이 검증되었으면 그 판매처 가격을 AI 추천 근거로 사용한다. priceSourceUrl/Name을 함께 반환하고 화면 링크도 실제 출처로 표시한다. 판매처 변경 시 이전 가격/하락률은 비워 교차 판매처 할인을 만들지 않는다. 유효한 주 가격이 있으면 더 싼 대체가만으로 바꾸지 않음.
- 허용 판매처 주소는 PcRetailSources로 분리. COMTRIX 파워96986270 및 삼성990EVOPlus1TB69869543 두 제품 연결. SSD 직접 페이지 조사 JSON-LD/메타 가격342400원 일치 확인(고정가격으로 코드에 넣지 않음). 출처 https://comtrix.kr/product/삼성전자-990-evo-plus-m-2-nvme-1tb/ . 검색 저장본343300과 달라 실제 수집 시각의 값을 사용해야 함.
- 모델명 대소문자·괄호만 정규화하고, 1TB/2TB 및 EVO/EVO Plus 혼동은 거부하는 검증 추가. 최신 parser6 + fallback3 테스트 작성. 사용자 직전 BAT3 통과는 fallback 추가 전 결과이며, 최종 변경의 BAT3/실수집BAT4 결과 대기 중이다. 에이전트가 테스트를 재실행하지 않음.

## FPS 참고 실측·임시 추정 — 2026-10-19
- 새 역할별 코드: PcFps DTO, PcFpsEvidence 조회, PcFpsProjection 조건부 참고 전이, 검증된 원문3개 측정항목 resources/pc-benchmarks/verified-reference.json, 프런트 PcFpsEvidence 컴포넌트/pcFps 모델.
- 출처 Notebookcheck Dell OptiPlex Micro Plus7010 리뷰와 UHD770 벤치마크표. Dota2 FHD High44.2/Ultra40.8(동일 i7-13700/16GB DDR5-4800), EldenRing FHD Low20.6(i9-13900HX, RAM미확인). 이미지/기사 본문 대량 복제 없음. 측정 PC/옵션/게시일 또는 측정일 미공개/게임빌드·렌더링 정보 미확인을 함께 표시.
- REFERENCE_PROJECTION은 동일 CPU/GPU이고 현재 RAM용량이 알려진 측정 RAM 이상인 경우만. 같은 옵션·해상도만 묶고 PC 중복을 제거한 평균실측 중앙값을 반올림한다. 임의 오차범위, 점수 비례, FHD→QHD/4K 배율, 메모리·세대 숫자에 의한 성능서열은 사용하지 않음.
- 현재 Dota2 FHD High 약44FPS만 저신뢰 참고 예상 가능. 이는 과거 외부 PC 측정값의 전이이며 현재 PC 성능 예측의 정확도를 검증한 모델이 아니다. RAM 세대/속도/채널·전력·드라이버·게임빌드 차이를 보정하지 못함. 통계적 신뢰구간/현재 PC 실측/실행 보장으로 표현 금지.
- EldenRing CPU불일치는 REFERENCE_ONLY20.6. 미확보 게임/해상도는 NO_DATA. 모든 보유게임/FHD QHD 4K의 예상FPS를 완료한 상태가 아님. 실측 자료 확대와 오차 검증이 남음.
- 서버가 수치·출처를 제공하고 AI는 조건/한계를 설명. AI가 숫자FPS를 생성하지 않음. 기존 Resolution2인자 생성자 유지. AI실패와 별개로 참고 근거 조회 가능.
- 새 back9/front3 FPS테스트 작성. 판매처 후속과 합쳐 최종 변경 BAT3/4는 사용자 실행 대기, 이번 변경의 통과/실API성공으로 표시하지 않음. BAT4는 AI호출없이 Dota2·EldenRing 근거 계약과다른판매처 가격을 함께 로그에 출력한다.


## 사용자 BAT4 실제 확인(2026-10-19)
- back-prepare PASS 및 official PASS/exit0. 최신 contractVersion 확인 후 실제 응답 조회, 임시회원 삭제204. 에이전트는 검사를 재실행하지 않고 사용자 로그/결과파일을 읽었다.
- Dota2 FHD REFERENCE_PROJECTION/LOW/약44FPS/High 조건, EldenRing FHD REFERENCE_ONLY20.6FPS/다른CPU, 미확보 해상도NO_DATA 실제 응답 확인.
- 제품11개와 COMTRIX 대체가2개 OBSERVED 확인: 삼성990EVOPlus1TB342400원, 마이크로닉스600W실버67800원. 2026-10-19 10:17UTC 관측값. 일반 가격이며 특가/할인율 아님.
- Windows PowerShell의 JSON 문자셋 추측 때문에 로그/보관JSON 한글이 깨진 점 발견. 검사 도구가 원본 응답 바이트를 UTF8로 읽도록 변경. 기존 결과는 변경하지 않고 오프라인 요약에서만 복원: outputs/PC-다중판매처-FPS-BAT4-확인.json. 이 요약 생성은 새 테스트/네트워크/AI 호출이 아니다. 도구 문자셋 수정의 실제 재실행은 아직 미확인.
- FPS 도입 전 '벤치마크가 제공되지 않았다'는 AI 지시/공식비교 문구를 현재PC실측과외부참고를구분하도록 정정. NO_DATA 문구도 자동수집중을암시하지않는'FPS근거부족'으로변경.
- 최신 변경의 전체 테스트/lint/build는 사용자 BAT3 대기. 기존184개/51개 통과를 최신 변경의 통과로 쓰지 않는다. 이번 묶음은 아직 commit/push/PR 반영 전이다.


## 최신 사용자 BAT 검증 — 2026-10-19 19:22
사용자가 BAT 3을 다시 실행한 로그 확인: front 54개, lint/build PASS; backend 전체 202개, 실패/오류 0. BAT 4 19:17의 공식 사양·복수 판매처·FPS 응답 검증도 PASS. 새 FPS 문맥을 포함한 실제 AI 재호출은 아직 검증하지 않았다. 예상 약44FPS는 동일 CPU/GPU의 과거 Dota 2 참고 실측을 옮긴 낮은 신뢰도 값이며 개인 PC 실측/검증된 예측 모델이 아니다.


## A~E 성능 등급 및 AI FPS 해설 수정 — 2026-10-19
- 사용자 요청으로 O/X 성능 배지를 A~E로 교체. FHD 높음 옵션의 참고 예상 평균FPS: A>=120, B>=60, C>=30, D>=20, E<20. 경계는 연속값이며 UI의 정수 구간은 표시용.
- 현재는 AI READY + 동일 CPU/GPU 참고 예상 + FHD high 조건에 맞는 결과만 등급 산정. 자료 없음/다른 옵션/AI 미완료는 판정 보류이며 E로 취급하지 않음.
- 종합은 산정 가능한 게임 FPS 중앙값. 반영 게임 수/전체 분석 수 표시. 표본이 일부이면 전체 보유게임을 대표하지 않음. 낮은 신뢰의 과거 실측 전이이며 정확도가 검증된 개인 PC 예측 모델이 아님.
- 기존 API grade O/X는 최소 사양 검증용 내부 호환 필드로 유지하며 성능 A~E와 구분. A~E 기준은 게임 실행 보장/구매 필요성/호환 인증이 아님.
- 게임 선택 펼침 카드 삭제. 상단에 작은 보유게임 가져오기/다른 게임 검색하기 버튼을 직접 표시.
- BAT7 실패 원인: Groq FHD 설명의 숫자FPS가 서버 TEXT_VALIDATION 규칙에 걸림. AI에는 FPS 수치 대신 조건/한계 context만 제공하고 숫자는 서버 근거 UI에 유지. 사용자가 수정 후 BAT7 PASS/READY 확인. 임시회원 삭제204.
- 후속 등급/버튼 테스트와 lint/build는 사용자 BAT3 대기. 기존 front54/backend202 통과는 후속 변경 이전 기록이며 최신 전체 통과로 간주하지 않음.


### 최종 후속 검증 — 2026-10-19 19:55
사용자가 BAT3 재실행: front57 테스트/lint/build PASS, backend 전체203 실패오류0. A~E/버튼/AI FPS context 및 회귀 포함. 수정 후 BAT7 실제 Groq Dota2 READY도 확인. 실제 UI 등급 렌더링의 별도 자동 브라우저 검증은 미실행.


## FPS 추가 조사(미검증 후속)
CS2 다른CPU FHD3옵션, 타이니 티나 동일CPU FHD Low1건 추가: 실측자료3→7건/게임2→4종. Low를High로 변환하지 않으며 CS2는 다른CPU 참고전용. 보유상위5 중 PUBG720p/Overwatch80%scale는FHD로 미채택, Isaac/HollowKnight명확한평균실측미확보. 상세 outputs/PC-FPS-추가조사-2026-10-19.md. 이 변경의 테스트는 사용자 BAT2 대기, 전체게임등급완료아님.

2026-10-19 20:12 사용자 BAT2 검증: backend 전체205, 실패/오류0 및 BUILD SUCCESSFUL. 추가FPS자료/옵션구분 회귀 통과. 새 자료의 실제 AI 재호출은 하지 않았으며, 앞선 Dota2 READY 검증과 구분.


## 고정 대기 제거 및 5게임 묶음 분석 — 2026-10-19 20:28
- 실제 사진/게임 요청 스레드에서 기존 Groq Thread.sleep 대기를 확인. 고정60초 대기 제거. 실제 잔여토큰 부족은 즉시429로 안내하며 자동재시도하지 않음.
- 인증 POST /api/ai/pc/assess-batch: 사양+중복없는 최대5개 appIds. 공식요구사양과 가격은 수집/캐시 재사용 후 AI 한 요청. 응답 길이/순서ID/게임별 등급·추천·텍스트 검증 적용, 실패를 READY로 처리하지 않음.
- 프런트 상위5개 묶음 요청, 취소/사양교체/로그아웃 늦은응답폐기 유지. 묶음은 완료 후 결과를 함께 표시. 개별 재분석 API 유지. 사진미입력/진행중/순서대기 문구 구분.
- 사용자 BAT3: front57/lint/build PASS, backend207 실패오류0. 서버 새코드 재시작20:27:57 확인. 새 묶음 실제외부AI 응답/실제사진 지연시간은 아직 검증 전. 즉시응답 보장 아님.
