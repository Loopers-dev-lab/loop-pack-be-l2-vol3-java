package com.loopers.batch.job.ranking.step;

import org.springframework.batch.item.ItemProcessor;

import java.util.concurrent.atomic.AtomicInteger;

public class RankingScoreProcessor implements ItemProcessor<AggregatedMetricRow, RankingScoreRow> {

    private static final double VIEW_WEIGHT = 0.1;
    private static final double LIKE_WEIGHT = 0.2;
    private static final double ORDER_WEIGHT = 0.7;

    private final AtomicInteger rankCounter = new AtomicInteger(0);

    @Override
    public RankingScoreRow process(AggregatedMetricRow item) {
        double score = item.viewCount() * VIEW_WEIGHT
                + item.likeCount() * LIKE_WEIGHT
                + Math.log1p(item.orderAmount()) * ORDER_WEIGHT;

        int ranking = rankCounter.incrementAndGet();

        return new RankingScoreRow(
                item.productId(),
                item.viewCount(),
                item.likeCount(),
                item.orderAmount(),
                score,
                ranking
        );
    }
}
