package com.ecommerce.engine.service;

import java.time.LocalDate;

public interface BatchJobService {

    void runDailySalesAggregation(LocalDate salesDate);
}
