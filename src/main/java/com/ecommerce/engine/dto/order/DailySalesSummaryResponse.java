package com.ecommerce.engine.dto.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record DailySalesSummaryResponse(
    LocalDate salesDate,
    long ordersCount,
    long unitsSold,
    BigDecimal grossRevenue,
    Instant updatedAt
) {
}
