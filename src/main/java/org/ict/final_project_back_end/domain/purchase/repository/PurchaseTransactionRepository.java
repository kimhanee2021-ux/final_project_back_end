package org.ict.final_project_back_end.domain.purchase.repository;

import org.ict.final_project_back_end.domain.purchase.entity.PurchaseTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface PurchaseTransactionRepository extends JpaRepository<PurchaseTransaction, Long> {

  @Query("SELECT t.externalKey FROM PurchaseTransaction t WHERE t.user.id = :userId")
  Set<String> findExternalKeysByUserId(@Param("userId") Long userId);

  // 소비 분석용: 거래와 상품을 한 번에 가져온다 (최근 거래 먼저)
  @Query("SELECT DISTINCT t FROM PurchaseTransaction t LEFT JOIN FETCH t.items WHERE t.user.id = :userId ORDER BY t.purchasedAt DESC, t.id DESC")
  List<PurchaseTransaction> findAllWithItemsByUserId(@Param("userId") Long userId);

  // 다시 가져오기(초기화)와 회원 탈퇴용. 상품을 먼저 지우고 거래를 지운다 (호출하는 쪽에서 트랜잭션 필요)
  @Modifying
  @Query("DELETE FROM PurchaseItem i WHERE i.transaction.id IN (SELECT t.id FROM PurchaseTransaction t WHERE t.user.id = :userId)")
  void deleteItemsByUserId(@Param("userId") Long userId);

  @Modifying
  @Query("DELETE FROM PurchaseTransaction t WHERE t.user.id = :userId")
  int deleteTransactionsByUserId(@Param("userId") Long userId);
}
