package org.ict.final_project_back_end.domain.analysis.controller;

import org.ict.final_project_back_end.domain.analysis.controller.docs.AnalysisApiDocs;
import org.ict.final_project_back_end.domain.analysis.dto.SpendingAnalysisResponse;
import org.ict.final_project_back_end.domain.analysis.service.SpendingAnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 개인 소비 분석 API. 실제 로직은 SpendingAnalysisService에 위임하고 HTTP 매핑만 담당한다.
@RestController
@RequestMapping("/api/analysis")
public class AnalysisController implements AnalysisApiDocs {

  private final SpendingAnalysisService spendingAnalysisService;

  public AnalysisController(SpendingAnalysisService spendingAnalysisService) {
    this.spendingAnalysisService = spendingAnalysisService;
  }

  @Override
  @GetMapping("/spending")
  public ResponseEntity<SpendingAnalysisResponse> getMySpending(@AuthenticationPrincipal Long userId) {
    return ResponseEntity.ok(spendingAnalysisService.getMySpending(userId));
  }
}
