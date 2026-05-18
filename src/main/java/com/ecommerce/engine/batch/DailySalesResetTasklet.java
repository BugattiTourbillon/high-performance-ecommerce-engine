package com.ecommerce.engine.batch;

import com.ecommerce.engine.repository.DailySalesSummaryRepository;
import com.ecommerce.engine.entity.DailySalesSummary;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@StepScope
@RequiredArgsConstructor
public class DailySalesResetTasklet implements Tasklet {

    private final DailySalesSummaryRepository dailySalesSummaryRepository;

    @Value("#{jobParameters['salesDate']}")
    private String salesDate;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        LocalDate summaryDate = LocalDate.parse(salesDate);
        dailySalesSummaryRepository.deleteBySalesDate(summaryDate);

        DailySalesSummary emptySummary = new DailySalesSummary();
        emptySummary.setSalesDate(summaryDate);
        emptySummary.setOrdersCount(0);
        emptySummary.setUnitsSold(0);
        emptySummary.setGrossRevenue(BigDecimal.ZERO);
        dailySalesSummaryRepository.save(emptySummary);
        return RepeatStatus.FINISHED;
    }
}
