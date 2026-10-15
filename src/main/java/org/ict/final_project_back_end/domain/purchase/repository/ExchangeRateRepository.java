package org.ict.final_project_back_end.domain.purchase.repository;

import org.ict.final_project_back_end.domain.purchase.entity.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {

  Optional<ExchangeRate> findByCurrencyAndRequestedDate(String currency, LocalDate requestedDate);
}
