package org.ict.final_project_back_end.domain.aiPc.repository;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.ict.final_project_back_end.domain.aiPc.entity.PcRequirementSnapshot;
public interface PcRequirementRepository extends JpaRepository<PcRequirementSnapshot,Long>{Optional<PcRequirementSnapshot> findFirstByAppIdOrderByIdDesc(long appId);}
