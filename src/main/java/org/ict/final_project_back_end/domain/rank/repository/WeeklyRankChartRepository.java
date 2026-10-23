package org.ict.final_project_back_end.domain.rank.repository;

import org.ict.final_project_back_end.domain.rank.entity.WeeklyRankChart;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface WeeklyRankChartRepository extends JpaRepository<WeeklyRankChart, Long> {

  Optional<WeeklyRankChart> findByCountryCodeAndWeekStart(String countryCode, LocalDate weekStart);
}
