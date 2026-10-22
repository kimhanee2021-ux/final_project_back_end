package org.ict.final_project_back_end.domain.budget.controller;

import jakarta.validation.Valid;
import org.ict.final_project_back_end.domain.budget.dto.BudgetRequest;
import org.ict.final_project_back_end.domain.budget.dto.BudgetResponse;
import org.ict.final_project_back_end.domain.budget.service.BudgetService;
import org.ict.final_project_back_end.global.swagger.BudgetApiDocs;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 월 게임 지출 예산 API. 실제 로직은 BudgetService에 위임하고 HTTP 매핑만 담당한다.
@RestController
@RequestMapping("/api/budgets")
public class BudgetController implements BudgetApiDocs {

  private final BudgetService budgetService;

  public BudgetController(BudgetService budgetService) {
    this.budgetService = budgetService;
  }

  @Override
  @GetMapping("/current")
  public ResponseEntity<BudgetResponse> getCurrent(@AuthenticationPrincipal Long userId) {
    return ResponseEntity.ok(budgetService.getCurrent(userId));
  }

  @Override
  @PutMapping("/current")
  public ResponseEntity<BudgetResponse> setCurrent(@AuthenticationPrincipal Long userId,
                                                   @Valid @RequestBody BudgetRequest request) {
    return ResponseEntity.ok(budgetService.setCurrent(userId, request));
  }
}
