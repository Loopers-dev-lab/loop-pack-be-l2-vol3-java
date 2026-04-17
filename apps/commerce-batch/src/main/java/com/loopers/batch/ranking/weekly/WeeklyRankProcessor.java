package com.loopers.batch.ranking.weekly;

import com.loopers.batch.ranking.RankingAggregateRow;
import com.loopers.domain.rank.MvProductRankWeekly;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

// @StepScope 필수 — 매 Job 실행마다 rankCounter 가 초기화되어야 함
@StepScope
@Component
public class WeeklyRankProcessor implements ItemProcessor<RankingAggregateRow, MvProductRankWeekly> {

    private final LocalDate snapshotDate;
    private final AtomicInteger rankCounter = new AtomicInteger(0);

    public WeeklyRankProcessor(@Value("#{jobParameters['snapshotDate']}") String snapshotDateStr) {
        this.snapshotDate = LocalDate.parse(snapshotDateStr);
    }

    @Override
    public MvProductRankWeekly process(RankingAggregateRow item) {
        int rank = rankCounter.incrementAndGet();
        return new MvProductRankWeekly(
            snapshotDate, item.productId(), rank, item.score(),
            item.viewCount(), item.likeCount(), item.orderRevenue());
    }
}
