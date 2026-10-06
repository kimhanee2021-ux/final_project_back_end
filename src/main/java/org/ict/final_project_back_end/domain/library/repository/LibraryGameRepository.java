package org.ict.final_project_back_end.domain.library.repository;

import org.ict.final_project_back_end.domain.library.entity.LibraryGame;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LibraryGameRepository extends JpaRepository<LibraryGame, Long> {

  List<LibraryGame> findAllByUserId(Long userId);

  List<LibraryGame> findAllByUserIdOrderByNameAsc(Long userId);

  // 사용자 삭제 시 FK 제약을 피하기 위해 해당 사용자의 보유 게임을 모두 지운다 (호출하는 쪽에서 트랜잭션 필요)
  void deleteByUserId(Long userId);
}
