package com.ecommerce.engine.batch;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailySalesDelta(
    LocalDate salesDate,
    long ordersCount,
    long unitsSold,
    BigDecimal grossRevenue
) {
}
