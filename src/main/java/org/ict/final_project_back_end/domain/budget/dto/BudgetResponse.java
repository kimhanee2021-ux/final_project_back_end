package org.ict.final_project_back_end.domain.budget.dto;

// month: "2026-10" 형식, limitAmount: 설정하지 않았으면 null
public record BudgetResponse(String month, Integer limitAmount) {
}
