package org.ict.final_project_back_end.domain.aiPc.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.ict.final_project_back_end.domain.aiPc.entity.PcPartPriceSnapshot;
import java.util.List;
public interface PcPartPriceRepository extends JpaRepository<PcPartPriceSnapshot,Long> {List<PcPartPriceSnapshot> findTop2ByProductCodeOrderByIdDesc(long code);}
