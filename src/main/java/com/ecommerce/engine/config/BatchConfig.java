package com.ecommerce.engine.config;

import com.ecommerce.engine.batch.DailySalesDelta;
import com.ecommerce.engine.batch.DailySalesResetTasklet;
import com.ecommerce.engine.batch.DailySalesSummaryWriter;
import com.ecommerce.engine.entity.CustomerOrder;
import com.ecommerce.engine.entity.OrderStatus;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class BatchConfig {

    @Bean
    public Job dailySalesAggregationJob(
        JobRepository jobRepository,
        @Qualifier("dailySalesResetStep") Step dailySalesResetStep,
        @Qualifier("dailySalesAggregationStep") Step dailySalesAggregationStep
    ) {
        return new JobBuilder("dailySalesAggregationJob", jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(dailySalesResetStep)
            .next(dailySalesAggregationStep)
            .build();
    }

    @Bean
    public Step dailySalesResetStep(
        JobRepository jobRepository,
        PlatformTransactionManager transactionManager,
        DailySalesResetTasklet dailySalesResetTasklet
    ) {
        return new StepBuilder("dailySalesResetStep", jobRepository)
            .tasklet(dailySalesResetTasklet, transactionManager)
            .build();
    }

    @Bean
    public Step dailySalesAggregationStep(
        JobRepository jobRepository,
        PlatformTransactionManager transactionManager,
        JpaPagingItemReader<CustomerOrder> dailySalesOrderReader,
        ItemProcessor<CustomerOrder, DailySalesDelta> dailySalesProcessor,
        DailySalesSummaryWriter dailySalesSummaryWriter
    ) {
        return new StepBuilder("dailySalesAggregationStep", jobRepository)
            .<CustomerOrder, DailySalesDelta>chunk(50, transactionManager)
            .reader(dailySalesOrderReader)
            .processor(dailySalesProcessor)
            .writer(dailySalesSummaryWriter)
            .build();
    }

    @Bean
    @StepScope
    public JpaPagingItemReader<CustomerOrder> dailySalesOrderReader(
        EntityManagerFactory entityManagerFactory,
        @Value("#{jobParameters['salesDate']}") String salesDate
    ) {
        LocalDate summaryDate = LocalDate.parse(salesDate);
        Instant start = summaryDate.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = summaryDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("status", OrderStatus.PAID);
        parameters.put("start", start);
        parameters.put("end", end);

        return new JpaPagingItemReaderBuilder<CustomerOrder>()
            .name("dailySalesOrderReader")
            .entityManagerFactory(entityManagerFactory)
            .queryString("""
                select o
                from CustomerOrder o
                where o.status = :status
                  and o.createdAt >= :start
                  and o.createdAt < :end
                order by o.id
                """)
            .parameterValues(parameters)
            .pageSize(50)
            .build();
    }

    @Bean
    public ItemProcessor<CustomerOrder, DailySalesDelta> dailySalesProcessor() {
        return order -> new DailySalesDelta(
            order.getCreatedAt().atOffset(ZoneOffset.UTC).toLocalDate(),
            1,
            order.getTotalItems(),
            order.getTotalAmount() == null ? BigDecimal.ZERO : order.getTotalAmount()
        );
    }
}
