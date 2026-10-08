package org.ict.final_project_back_end.domain.library.repository;

import org.ict.final_project_back_end.domain.library.entity.LibraryGamePrice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LibraryGamePriceRepository extends JpaRepository<LibraryGamePrice, Long> {

  List<LibraryGamePrice> findAllByAppIdIn(List<Long> appIds);
}
