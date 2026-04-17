package com.loopers.batch.job.ranking.step;

import com.loopers.batch.job.ranking.WeeklyRankingJobConfig;
import com.loopers.domain.ranking.ProductMetricsAggregation;
import com.loopers.domain.ranking.ProductRankWeekly;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

@StepScope
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = WeeklyRankingJobConfig.JOB_NAME)
@Component
public class WeeklyRankingProcessor implements ItemProcessor<ProductMetricsAggregation, ProductRankWeekly>, StepExecutionListener {

    private final AtomicInteger rankCounter = new AtomicInteger(0);
    private long nextVersion;

    @Value("#{jobParameters['requestDate']}")
    private LocalDate requestDate;

    @Override
    public void beforeStep(StepExecution stepExecution) {
        this.nextVersion = stepExecution.getJobExecution()
            .getExecutionContext()
            .getLong("nextWeeklyVersion");
    }

    @Override
    public ProductRankWeekly process(ProductMetricsAggregation item) {
        LocalDate endDate = requestDate;
        LocalDate startDate = endDate.minusDays(6);
        int rank = rankCounter.incrementAndGet();

        return new ProductRankWeekly(
            item.productId(),
            rank,
            item.score(),
            item.totalViewCount(),
            item.totalLikeCount(),
            item.totalSaleCount(),
            startDate,
            endDate,
            nextVersion
        );
    }
}
