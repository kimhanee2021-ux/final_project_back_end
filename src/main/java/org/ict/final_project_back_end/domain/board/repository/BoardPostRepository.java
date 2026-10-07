package org.ict.final_project_back_end.domain.board.repository;

import org.ict.final_project_back_end.domain.board.entity.BoardPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface BoardPostRepository extends JpaRepository<BoardPost, Long>, JpaSpecificationExecutor<BoardPost> {
}
