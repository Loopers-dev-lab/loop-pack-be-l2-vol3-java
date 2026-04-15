package com.loopers.batch.job.ranking.monthly.step;

import com.loopers.batch.job.ranking.monthly.MonthlyRankingJobConfig;
import com.loopers.domain.ranking.MvProductRankMonthly;
import com.loopers.domain.ranking.ProductAggregation;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

@StepScope
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = MonthlyRankingJobConfig.JOB_NAME)
@Component
public class MonthlyRankingProcessor implements ItemProcessor<ProductAggregation, MvProductRankMonthly> {

    // Step 생명주기 동안 rank 순번을 유지 (@StepScope 이므로 Step 종료 시 폐기)
    private final AtomicInteger rankCounter = new AtomicInteger(0);

    // baseDate 는 해당 월의 1일로 저장 (예: 202604 → 2026-04-01)
    private LocalDate baseDate;

    @Value("#{jobParameters['targetYearMonth']}")
    public void setTargetYearMonth(String targetYearMonth) {
        this.baseDate = YearMonth.parse(targetYearMonth, DateTimeFormatter.ofPattern("yyyyMM")).atDay(1);
    }

    @Override
    public MvProductRankMonthly process(ProductAggregation item) {
        int rank = rankCounter.incrementAndGet();
        if (rank > 100) {
            return null; // 101위 이후는 null 반환 → Writer 에서 제외
        }
        double score = item.totalView() * 0.1
                + item.totalLike() * 0.2
                + 0.7 * Math.log1p(item.totalSales());
        return MvProductRankMonthly.of(
                item.productId(), rank, score,
                item.totalLike(), item.totalOrder(), item.totalView(),
                item.totalSales(), baseDate
        );
    }
}
