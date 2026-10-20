package org.ict.final_project_back_end.domain.purchase.service;

import org.ict.final_project_back_end.domain.purchase.dto.LicenseImportRequest;
import org.ict.final_project_back_end.domain.purchase.dto.LicenseImportResponse;

// 스팀 라이선스 페이지(브라우저에서 파싱한 목록)를 저장하는 서비스.
public interface LicenseImportService {

  // 금액이 없어서 환율·스팀 확인 없이 내 보유 게임 이름으로만 연결한다. 이미 가져온 라이선스는 건너뛴다.
  LicenseImportResponse importLicenses(Long userId, LicenseImportRequest request);
}
