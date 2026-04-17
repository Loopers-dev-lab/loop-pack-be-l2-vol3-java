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
import java.util.concurrent.atomic.AtomicInteger;

@StepScope
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = WeeklyRankingJobConfig.JOB_NAME)
@Component
public class WeeklyRankingProcessor implements ItemProcessor<ProductAggregation, MvProductRankWeekly> {

    // Step 생명주기 동안 rank 순번을 유지 (@StepScope 이므로 Step 종료 시 폐기)
    private final AtomicInteger rankCounter = new AtomicInteger(0);

    private LocalDate targetDate;

    @Value("#{jobParameters['targetDate']}")
    public void setTargetDate(LocalDate targetDate) {
        this.targetDate = targetDate;
    }

    @Override
    public MvProductRankWeekly process(ProductAggregation item) {
        int rank = rankCounter.incrementAndGet();
        return MvProductRankWeekly.of(
                item.productId(), rank, item.score(),
                item.totalLike(), item.totalOrder(), item.totalView(),
                item.totalSales(), targetDate
        );
    }
}
