package org.ict.final_project_back_end.domain.rank.repository;

import org.ict.final_project_back_end.domain.rank.entity.WeeklyRankEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WeeklyRankEntryRepository extends JpaRepository<WeeklyRankEntry, Long> {

  List<WeeklyRankEntry> findTop10ByChart_ChartIdOrderByGameRankAsc(Long chartId);
}
