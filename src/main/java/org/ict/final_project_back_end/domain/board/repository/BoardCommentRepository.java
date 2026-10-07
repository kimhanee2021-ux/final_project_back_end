package org.ict.final_project_back_end.domain.board.repository;
import java.util.Optional;
import org.ict.final_project_back_end.domain.board.entity.BoardComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
public interface BoardCommentRepository extends JpaRepository<BoardComment, Long> {
  @EntityGraph(attributePaths = "author")
  Page<BoardComment> findByPostId(Long postId, Pageable pageable);
  Optional<BoardComment> findByIdAndPostId(Long id, Long postId);
  boolean existsByParentId(Long parentId);
}
