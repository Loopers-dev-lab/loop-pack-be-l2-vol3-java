package com.loopers.batch.job.ranking.step;

import com.loopers.batch.job.ranking.MonthlyRankingJobConfig;
import com.loopers.batch.job.ranking.ProductScoreRow;
import com.loopers.domain.ranking.MvProductRankMonthly;
import com.loopers.domain.ranking.RankingScoreCalculator;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@StepScope
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = MonthlyRankingJobConfig.JOB_NAME)
@Component
public class MonthlyRankingProcessor implements ItemProcessor<ProductScoreRow, MvProductRankMonthly> {

    private int rankCounter;

    @Value("#{jobParameters['monthStartDate']}")
    private String monthStartDate;

    @Override
    public MvProductRankMonthly process(ProductScoreRow item) {
        RankingScoreCalculator.assertConsistent(
            item.getTotalScore(),
            item.getViewCount(), item.getLikeCount(), item.getOrderCount()
        );

        LocalDate startDate = LocalDate.parse(monthStartDate, DateTimeFormatter.BASIC_ISO_DATE);
        return MvProductRankMonthly.of(
            item.getProductId(), startDate, item.getTotalScore(),
            item.getViewCount(), item.getLikeCount(), item.getOrderCount(),
            ++rankCounter
        );
    }
}
