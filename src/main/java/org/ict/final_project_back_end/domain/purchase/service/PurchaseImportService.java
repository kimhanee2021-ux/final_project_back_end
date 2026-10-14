package org.ict.final_project_back_end.domain.purchase.service;

import org.ict.final_project_back_end.domain.purchase.dto.PurchaseDeleteResponse;
import org.ict.final_project_back_end.domain.purchase.dto.PurchaseImportRequest;
import org.ict.final_project_back_end.domain.purchase.dto.PurchaseImportResponse;

// 스팀 구매내역(브라우저에서 파싱한 거래 목록)을 저장하고, 가져온 기록을 초기화하는 서비스.
public interface PurchaseImportService {

  // 이미 가져온 거래는 건너뛰고, 결제 금액은 구매일 환율로 원화로 바꿔 저장한다.
  // 상품 이름은 보유 게임·게임 목록·스팀 검색으로 appId에 연결한다(DLC는 본편에 연결).
  PurchaseImportResponse importHistory(Long userId, PurchaseImportRequest request);

  // 가져온 구매내역과 라이선스를 모두 지운다 (다시 가져오기 전 초기화).
  PurchaseDeleteResponse deleteAll(Long userId);
}
