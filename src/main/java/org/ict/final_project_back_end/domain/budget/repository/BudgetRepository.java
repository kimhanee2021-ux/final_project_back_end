package org.ict.final_project_back_end.domain.budget.repository;

import org.ict.final_project_back_end.domain.budget.entity.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

  Optional<Budget> findByUserIdAndBudgetMonth(Long userId, String budgetMonth);

  // 회원 탈퇴 시 함께 지운다 (budgets.user_id가 users를 참조)
  @Modifying
  @Query("delete from Budget b where b.user.id = :userId")
  int deleteByUserId(@Param("userId") Long userId);
}
