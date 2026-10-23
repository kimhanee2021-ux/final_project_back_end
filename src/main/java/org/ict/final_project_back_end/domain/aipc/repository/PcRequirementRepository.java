package org.ict.final_project_back_end.domain.aipc.repository;

import java.util.Optional;
import org.ict.final_project_back_end.domain.aipc.entity.PcRequirementSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PcRequirementRepository extends JpaRepository<PcRequirementSnapshot, Long> {
  Optional<PcRequirementSnapshot> findFirstByAppIdOrderByIdDesc(long appId);
}
