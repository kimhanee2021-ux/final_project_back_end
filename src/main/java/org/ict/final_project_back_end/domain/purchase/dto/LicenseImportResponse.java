package org.ict.final_project_back_end.domain.purchase.dto;

// imported: 새로 저장한 라이선스 수, duplicates: 이미 가져온 적 있어 건너뛴 수
// matched: 그중 보유 게임에 연결된 수 (데모·도구·옛 패키지 등은 연결되지 않아도 정상)
public record LicenseImportResponse(int imported, int duplicates, int matched) {
}
