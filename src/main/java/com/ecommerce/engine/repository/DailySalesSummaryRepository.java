package com.ecommerce.engine.repository;

import com.ecommerce.engine.entity.DailySalesSummary;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailySalesSummaryRepository extends JpaRepository<DailySalesSummary, Long> {

    Optional<DailySalesSummary> findBySalesDate(LocalDate salesDate);

    void deleteBySalesDate(LocalDate salesDate);
}
