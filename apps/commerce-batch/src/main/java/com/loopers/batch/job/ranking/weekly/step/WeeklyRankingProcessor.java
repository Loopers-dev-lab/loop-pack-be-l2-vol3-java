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

    @Value("#{jobParameters['targetDate']}")
    private LocalDate targetDate;

    @Override
    public MvProductRankWeekly process(ProductAggregation item) {
        int rank = rankCounter.incrementAndGet();
        if (rank > 100) {
            return null; // 101위 이후는 null 반환 → Writer 에서 제외
        }
        double score = item.totalView() * 0.1
                + item.totalLike() * 0.2
                + 0.7 * Math.log1p(item.totalSales());
        return MvProductRankWeekly.of(
                item.productId(), rank, score,
                item.totalLike(), item.totalOrder(), item.totalView(),
                item.totalSales(), targetDate
        );
    }
}
