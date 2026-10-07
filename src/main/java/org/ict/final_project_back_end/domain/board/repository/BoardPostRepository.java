package org.ict.final_project_back_end.domain.board.repository;

import org.ict.final_project_back_end.domain.board.entity.BoardPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface BoardPostRepository extends JpaRepository<BoardPost, Long>, JpaSpecificationExecutor<BoardPost> {

  @org.springframework.data.jpa.repository.Query("select count(c) from BoardComment c where c.post.id = :id")
  long countComments(@org.springframework.data.repository.query.Param("id") Long id);

  @org.springframework.data.jpa.repository.Query("select count(i) from BoardPostImage i where i.post.id = :id")
  long countImages(@org.springframework.data.repository.query.Param("id") Long id);
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select p from BoardPost p where p.id = :id")
  java.util.Optional<BoardPost> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
}
