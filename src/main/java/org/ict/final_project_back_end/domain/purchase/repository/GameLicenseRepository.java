package org.ict.final_project_back_end.domain.purchase.repository;

import org.ict.final_project_back_end.domain.purchase.entity.GameLicense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface GameLicenseRepository extends JpaRepository<GameLicense, Long> {

  @Query("SELECT l.externalKey FROM GameLicense l WHERE l.user.id = :userId")
  Set<String> findExternalKeysByUserId(@Param("userId") Long userId);

  // 소비 분석용: 보유 게임에 연결된 라이선스 (오래된 것 먼저 -> 게임별 첫 획득일)
  @Query("SELECT l FROM GameLicense l WHERE l.user.id = :userId AND l.appId IS NOT NULL ORDER BY l.acquiredAt ASC, l.id ASC")
  List<GameLicense> findMatchedByUserId(@Param("userId") Long userId);

  // 다시 가져오기(초기화)와 회원 탈퇴용 (호출하는 쪽에서 트랜잭션 필요)
  @Modifying
  @Query("DELETE FROM GameLicense l WHERE l.user.id = :userId")
  int deleteByUserId(@Param("userId") Long userId);
}
