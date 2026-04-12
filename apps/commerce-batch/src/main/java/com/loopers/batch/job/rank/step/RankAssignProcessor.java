package com.loopers.batch.job.rank.step;

import com.loopers.domain.rank.MvProductRankRow;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.item.ItemProcessor;

import java.util.concurrent.atomic.AtomicInteger;

@RequiredArgsConstructor
public class RankAssignProcessor implements ItemProcessor<AggregatedScoreRow, MvProductRankRow> {

    private final String periodKey;
    private final AtomicInteger rankCounter = new AtomicInteger(1);

    @Override
    public MvProductRankRow process(AggregatedScoreRow in) {
        return new MvProductRankRow(
                periodKey,
                rankCounter.getAndIncrement(),
                in.productDbId(),
                in.totalScore(),
                in.totalView(),
                in.totalLike(),
                in.totalOrder()
        );
    }
}
