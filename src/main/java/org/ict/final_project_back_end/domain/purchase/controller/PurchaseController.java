package org.ict.final_project_back_end.domain.purchase.controller;

import jakarta.validation.Valid;
import org.ict.final_project_back_end.domain.purchase.dto.LicenseImportRequest;
import org.ict.final_project_back_end.domain.purchase.dto.LicenseImportResponse;
import org.ict.final_project_back_end.domain.purchase.dto.PurchaseDeleteResponse;
import org.ict.final_project_back_end.domain.purchase.dto.PurchaseImportRequest;
import org.ict.final_project_back_end.domain.purchase.dto.PurchaseImportResponse;
import org.ict.final_project_back_end.domain.purchase.service.LicenseImportService;
import org.ict.final_project_back_end.domain.purchase.service.PurchaseImportService;
import org.ict.final_project_back_end.global.swagger.PurchaseApiDocs;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 스팀 구매내역·라이선스 가져오기 API. 실제 로직은 PurchaseImportService·LicenseImportService에 위임하고 HTTP 매핑만 담당한다.
@RestController
@RequestMapping("/api/purchases")
public class PurchaseController implements PurchaseApiDocs {

  private final PurchaseImportService purchaseImportService;
  private final LicenseImportService licenseImportService;

  public PurchaseController(PurchaseImportService purchaseImportService, LicenseImportService licenseImportService) {
    this.purchaseImportService = purchaseImportService;
    this.licenseImportService = licenseImportService;
  }

  @Override
  @PostMapping("/import")
  public ResponseEntity<PurchaseImportResponse> importHistory(@AuthenticationPrincipal Long userId,
                                                              @Valid @RequestBody PurchaseImportRequest request) {
    return ResponseEntity.ok(purchaseImportService.importHistory(userId, request));
  }

  @Override
  @PostMapping("/licenses/import")
  public ResponseEntity<LicenseImportResponse> importLicenses(@AuthenticationPrincipal Long userId,
                                                              @Valid @RequestBody LicenseImportRequest request) {
    return ResponseEntity.ok(licenseImportService.importLicenses(userId, request));
  }

  @Override
  @DeleteMapping
  public ResponseEntity<PurchaseDeleteResponse> deleteAll(@AuthenticationPrincipal Long userId) {
    return ResponseEntity.ok(purchaseImportService.deleteAll(userId));
  }
}
