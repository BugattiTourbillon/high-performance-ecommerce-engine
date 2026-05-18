package com.ecommerce.engine.batch;

import com.ecommerce.engine.entity.DailySalesSummary;
import com.ecommerce.engine.repository.DailySalesSummaryRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DailySalesSummaryWriter implements ItemWriter<DailySalesDelta> {

    private final DailySalesSummaryRepository dailySalesSummaryRepository;

    @Override
    public void write(Chunk<? extends DailySalesDelta> chunk) {
        Map<LocalDate, AggregateAccumulator> aggregated = new HashMap<>();
        for (DailySalesDelta delta : chunk.getItems()) {
            AggregateAccumulator accumulator = aggregated.computeIfAbsent(delta.salesDate(), ignored -> new AggregateAccumulator());
            accumulator.ordersCount += delta.ordersCount();
            accumulator.unitsSold += delta.unitsSold();
            accumulator.grossRevenue = accumulator.grossRevenue.add(delta.grossRevenue());
        }

        for (Map.Entry<LocalDate, AggregateAccumulator> entry : aggregated.entrySet()) {
            DailySalesSummary summary = dailySalesSummaryRepository.findBySalesDate(entry.getKey())
                .orElseGet(() -> {
                    DailySalesSummary created = new DailySalesSummary();
                    created.setSalesDate(entry.getKey());
                    created.setGrossRevenue(BigDecimal.ZERO);
                    return created;
                });
            summary.setOrdersCount(summary.getOrdersCount() + entry.getValue().ordersCount);
            summary.setUnitsSold(summary.getUnitsSold() + entry.getValue().unitsSold);
            summary.setGrossRevenue(summary.getGrossRevenue().add(entry.getValue().grossRevenue));
            dailySalesSummaryRepository.save(summary);
        }
    }

    private static final class AggregateAccumulator {
        private long ordersCount;
        private long unitsSold;
        private BigDecimal grossRevenue = BigDecimal.ZERO;
    }
}
