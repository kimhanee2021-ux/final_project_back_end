package org.ict.final_project_back_end.domain.budget.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

// 이번 달 게임 지출 예산(원). 0원도 허용한다 (이번 달은 사지 않기).
public record BudgetRequest(
    @NotNull @Min(0) @Max(10_000_000) Integer limitAmount
) {
}
