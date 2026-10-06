package org.ict.final_project_back_end.domain.library.repository;

import org.ict.final_project_back_end.domain.library.entity.LibraryGame;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LibraryGameRepository extends JpaRepository<LibraryGame, Long> {

  List<LibraryGame> findAllByUserId(Long userId);

  List<LibraryGame> findAllByUserIdOrderByNameAsc(Long userId);
}
