package org.ict.final_project_back_end.domain.budget.service;

import org.ict.final_project_back_end.domain.budget.dto.BudgetRequest;
import org.ict.final_project_back_end.domain.budget.dto.BudgetResponse;

// 사용자의 월 게임 지출 예산. 달은 한국 시간 기준 이번 달이다.
public interface BudgetService {

  // 이번 달 예산. 설정하지 않았으면 limitAmount가 null이다.
  BudgetResponse getCurrent(Long userId);

  // 이번 달 예산을 저장한다 (없으면 만들고, 있으면 금액만 바꾼다).
  BudgetResponse setCurrent(Long userId, BudgetRequest request);
}
