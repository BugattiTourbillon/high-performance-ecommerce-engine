package com.ecommerce.engine.service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SpringBatchJobService implements BatchJobService {

    private final JobLauncher jobLauncher;
    private final Job dailySalesAggregationJob;

    @Override
    public void runDailySalesAggregation(LocalDate salesDate) {
        try {
            JobParameters parameters = new JobParametersBuilder()
                .addString("salesDate", salesDate.toString())
                .addLong("requestedAt", System.currentTimeMillis())
                .toJobParameters();
            jobLauncher.run(dailySalesAggregationJob, parameters);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to execute daily sales batch job", ex);
        }
    }

    @Scheduled(cron = "${app.batch.daily-sales-cron}")
    public void runScheduledDailySalesAggregation() {
        LocalDate previousUtcDay = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        log.info("Launching scheduled daily sales aggregation for {}", previousUtcDay);
        runDailySalesAggregation(previousUtcDay);
    }
}
