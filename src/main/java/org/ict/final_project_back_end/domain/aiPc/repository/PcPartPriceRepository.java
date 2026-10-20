package org.ict.final_project_back_end.domain.aiPc.repository;

import java.util.List;
import org.ict.final_project_back_end.domain.aiPc.entity.PcPartPriceSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PcPartPriceRepository extends JpaRepository<PcPartPriceSnapshot, Long> {
  List<PcPartPriceSnapshot> findTop2ByProductCodeOrderByIdDesc(long code);

  List<PcPartPriceSnapshot> findTop2ByProductCodeAndSourceKeyOrderByIdDesc(
      long code, String sourceKey);
}
