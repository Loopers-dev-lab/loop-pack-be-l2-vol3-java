package com.loopers.batch.job.ranking.weekly.step;

import com.loopers.batch.job.ranking.weekly.WeeklyRankingJobConfig;
import com.loopers.domain.ranking.MvProductRankWeekly;
import com.loopers.domain.ranking.ProductAggregation;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@StepScope
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = WeeklyRankingJobConfig.JOB_NAME)
@Component
public class WeeklyRankingProcessor implements ItemProcessor<ProductAggregation, MvProductRankWeekly> {

    private LocalDate targetDate;

    @Value("#{jobParameters['targetDate']}")
    public void setTargetDate(LocalDate targetDate) {
        this.targetDate = targetDate;
    }

    @Override
    public MvProductRankWeekly process(ProductAggregation item) {
        return MvProductRankWeekly.of(
                item.productId(), item.rank(), item.score(),
                item.totalLike(), item.totalOrder(), item.totalView(),
                item.totalSales(), targetDate
        );
    }
}
