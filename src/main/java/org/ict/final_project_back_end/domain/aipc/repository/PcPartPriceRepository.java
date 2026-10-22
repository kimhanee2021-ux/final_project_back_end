package org.ict.final_project_back_end.domain.aipc.repository;

import java.util.List;
import org.ict.final_project_back_end.domain.aipc.entity.PcPartPriceSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PcPartPriceRepository extends JpaRepository<PcPartPriceSnapshot, Long> {
  List<PcPartPriceSnapshot> findTop2ByProductCodeAndSourceKeyOrderByIdDesc(
      long code, String sourceKey);
}
