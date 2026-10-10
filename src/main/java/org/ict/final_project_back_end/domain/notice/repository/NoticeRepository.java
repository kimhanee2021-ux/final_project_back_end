package org.ict.final_project_back_end.domain.notice.repository;

import org.ict.final_project_back_end.domain.notice.entity.Notice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeRepository extends JpaRepository<Notice, Long> { }
