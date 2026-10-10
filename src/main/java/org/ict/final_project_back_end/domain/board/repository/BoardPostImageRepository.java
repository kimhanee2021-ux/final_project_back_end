package org.ict.final_project_back_end.domain.board.repository;
import java.util.List;
import java.util.Optional;
import org.ict.final_project_back_end.domain.board.entity.BoardPostImage;
import org.springframework.data.jpa.repository.JpaRepository;
public interface BoardPostImageRepository extends JpaRepository<BoardPostImage, Long> {
  List<BoardPostImage> findByPostIdOrderBySortOrderAscIdAsc(Long postId);
  Optional<BoardPostImage> findByIdAndPostId(Long id, Long postId);
  Optional<BoardPostImage> findByPostIdAndImageUrl(Long postId, String imageUrl);
}
