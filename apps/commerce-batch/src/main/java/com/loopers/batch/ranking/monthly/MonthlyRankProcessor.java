package com.loopers.batch.ranking.monthly;

import com.loopers.batch.ranking.RankingAggregateRow;
import com.loopers.domain.rank.MvProductRankMonthly;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

@StepScope
@Component
public class MonthlyRankProcessor implements ItemProcessor<RankingAggregateRow, MvProductRankMonthly> {

    private final LocalDate snapshotDate;
    private final AtomicInteger rankCounter = new AtomicInteger(0);

    public MonthlyRankProcessor(@Value("#{jobParameters['snapshotDate']}") String snapshotDateStr) {
        this.snapshotDate = LocalDate.parse(snapshotDateStr);
    }

    @Override
    public MvProductRankMonthly process(RankingAggregateRow item) {
        int rank = rankCounter.incrementAndGet();
        return new MvProductRankMonthly(
            snapshotDate, item.productId(), rank, item.score(),
            item.viewCount(), item.likeCount(), item.orderRevenue());
    }
}
