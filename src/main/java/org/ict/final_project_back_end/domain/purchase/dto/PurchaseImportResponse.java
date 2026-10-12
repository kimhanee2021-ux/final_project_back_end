package org.ict.final_project_back_end.domain.purchase.dto;

import java.util.List;

// imported: 새로 저장한 거래 수, duplicates: 이미 가져온 적 있어 건너뛴 거래 수
// unmatchedItems: 게임에 연결하지 못한 상품 이름 (지출 합계에는 들어가고 게임별 집계에서만 빠짐)
// unsupportedCurrencies: 환율을 구하지 못해 원화로 바꾸지 못한 통화
public record PurchaseImportResponse(
    int imported,
    int duplicates,
    List<String> unmatchedItems,
    List<String> unsupportedCurrencies
) {
}
