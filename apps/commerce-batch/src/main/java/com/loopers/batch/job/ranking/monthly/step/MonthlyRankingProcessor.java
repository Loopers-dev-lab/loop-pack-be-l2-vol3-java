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

@StepScope
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = MonthlyRankingJobConfig.JOB_NAME)
@Component
public class MonthlyRankingProcessor implements ItemProcessor<ProductAggregation, MvProductRankMonthly> {

    // baseDate 는 해당 월의 1일로 저장 (예: 202604 → 2026-04-01)
    private LocalDate baseDate;

    @Value("#{jobParameters['targetYearMonth']}")
    public void setTargetYearMonth(String targetYearMonth) {
        this.baseDate = YearMonth.parse(targetYearMonth, DateTimeFormatter.ofPattern("yyyyMM")).atDay(1);
    }

    @Override
    public MvProductRankMonthly process(ProductAggregation item) {
        return MvProductRankMonthly.of(
                item.productId(), item.rank(), item.score(),
                item.totalLike(), item.totalOrder(), item.totalView(),
                item.totalSales(), baseDate
        );
    }
}
