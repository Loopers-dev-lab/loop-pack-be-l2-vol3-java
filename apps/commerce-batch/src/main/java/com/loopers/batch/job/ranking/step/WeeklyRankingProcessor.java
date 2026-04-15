package com.loopers.batch.job.ranking.step;

import com.loopers.batch.job.ranking.ProductScoreRow;
import com.loopers.batch.job.ranking.WeeklyRankingJobConfig;
import com.loopers.domain.ranking.MvProductRankWeekly;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@StepScope
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = WeeklyRankingJobConfig.JOB_NAME)
@Component
public class WeeklyRankingProcessor implements ItemProcessor<ProductScoreRow, MvProductRankWeekly> {

    private int rankCounter;

    @Value("#{jobParameters['weekStartDate']}")
    private String weekStartDate;

    @Override
    public MvProductRankWeekly process(ProductScoreRow item) {
        LocalDate startDate = LocalDate.parse(weekStartDate, DateTimeFormatter.BASIC_ISO_DATE);
        return MvProductRankWeekly.of(
            item.getProductId(), startDate, item.getTotalScore(),
            item.getViewCount(), item.getLikeCount(), item.getOrderCount(),
            ++rankCounter
        );
    }
}
