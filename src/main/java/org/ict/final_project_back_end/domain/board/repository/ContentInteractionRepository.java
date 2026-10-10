package org.ict.final_project_back_end.domain.board.repository;
import java.util.Optional;
import org.ict.final_project_back_end.domain.board.entity.ContentInteraction;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ContentInteractionRepository extends JpaRepository<ContentInteraction, Long> {
  Optional<ContentInteraction> findByTargetTypeAndTargetIdAndUserIdAndInteractionType(
      String targetType, Long targetId, Long userId, String interactionType);
  long countByTargetTypeAndTargetIdAndInteractionType(String targetType, Long targetId, String interactionType);
  void deleteByTargetTypeAndTargetId(String targetType, Long targetId);
}
