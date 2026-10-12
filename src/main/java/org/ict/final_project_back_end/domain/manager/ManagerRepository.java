package org.ict.final_project_back_end.domain.manager;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ManagerRepository extends JpaRepository<ManagerRecord,Long>{
 List<ManagerRecord> findByUserIdOrderByIdDesc(Long userId);
 List<ManagerRecord> findByUserIdAndKind(Long userId,String kind);
 Optional<ManagerRecord> findByIdAndUserId(Long id,Long userId);
 List<ManagerRecord> findByKindOrderByIdAsc(String kind);
}
