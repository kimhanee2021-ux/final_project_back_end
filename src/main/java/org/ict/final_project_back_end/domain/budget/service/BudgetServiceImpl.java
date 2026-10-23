package org.ict.final_project_back_end.domain.budget.service;

import org.ict.final_project_back_end.domain.budget.dto.BudgetRequest;
import org.ict.final_project_back_end.domain.budget.dto.BudgetResponse;
import org.ict.final_project_back_end.domain.budget.entity.Budget;
import org.ict.final_project_back_end.domain.budget.repository.BudgetRepository;
import org.ict.final_project_back_end.domain.user.entity.User;
import org.ict.final_project_back_end.domain.user.exception.UserNotFoundException;
import org.ict.final_project_back_end.domain.user.repository.UserRepository;
import org.ict.final_project_back_end.global.exception.AuthenticationRequiredException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class BudgetServiceImpl implements BudgetService {

  private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

  private final BudgetRepository budgetRepository;
  private final UserRepository userRepository;

  public BudgetServiceImpl(BudgetRepository budgetRepository, UserRepository userRepository) {
    this.budgetRepository = budgetRepository;
    this.userRepository = userRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public BudgetResponse getCurrent(Long userId) {
    requireLogin(userId);
    String month = currentMonth();
    return new BudgetResponse(month, budgetRepository.findByUserIdAndBudgetMonth(userId, month)
        .map(Budget::getLimitAmount).orElse(null));
  }

  @Override
  @Transactional
  public BudgetResponse setCurrent(Long userId, BudgetRequest request) {
    requireLogin(userId);
    String month = currentMonth();
    Budget budget = budgetRepository.findByUserIdAndBudgetMonth(userId, month).orElse(null);
    if (budget == null) {
      User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
      budget = budgetRepository.save(Budget.builder().user(user).budgetMonth(month).limitAmount(request.limitAmount()).build());
    } else {
      budget.changeLimit(request.limitAmount());
    }
    return new BudgetResponse(month, budget.getLimitAmount());
  }

  static String currentMonth() {
    return LocalDate.now(KOREA).toString().substring(0, 7);
  }

  private void requireLogin(Long userId) {
    if (userId == null) {
      throw new AuthenticationRequiredException();
    }
  }
}
