package com.ecommerce.engine.service;

import com.ecommerce.engine.dto.order.DailySalesSummaryResponse;
import com.ecommerce.engine.entity.DailySalesSummary;
import com.ecommerce.engine.exception.ResourceNotFoundException;
import com.ecommerce.engine.repository.DailySalesSummaryRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SalesReportingService {

    private final DailySalesSummaryRepository dailySalesSummaryRepository;

    @Transactional(readOnly = true)
    public DailySalesSummaryResponse getSummary(LocalDate salesDate) {
        DailySalesSummary summary = dailySalesSummaryRepository.findBySalesDate(salesDate)
            .orElseThrow(() -> new ResourceNotFoundException("Daily sales summary not found for date: " + salesDate));
        return new DailySalesSummaryResponse(
            summary.getSalesDate(),
            summary.getOrdersCount(),
            summary.getUnitsSold(),
            summary.getGrossRevenue(),
            summary.getUpdatedAt()
        );
    }
}
