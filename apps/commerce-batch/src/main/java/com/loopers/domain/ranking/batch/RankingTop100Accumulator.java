package com.loopers.domain.ranking.batch;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * product_metrics 전체를 스캔하면서 상위 100개만 유지한다. 동점은 product_id 오름차순이 앞 순위.
 */
public class RankingTop100Accumulator {

    private static final int CAPACITY = 100;

    /**
     * 힙 순서: 더 나쁜(점수 낮음, 동점이면 id 큼) 후보가 루트.
     */
    private static final Comparator<RankingScoreCandidate> WORST_TO_BETTER =
            Comparator.comparingDouble(RankingScoreCandidate::score)
                    .thenComparing(Comparator.comparingLong(RankingScoreCandidate::productId).reversed());

    private final PriorityQueue<RankingScoreCandidate> minHeap = new PriorityQueue<>(WORST_TO_BETTER);

    /**
     * 후보를 반영한다. 상위 100을 넘으면 가장 낮은 후보를 제거한다.
     */
    public void accept(RankingScoreCandidate candidate) {
        if (minHeap.size() < CAPACITY) {
            minHeap.add(candidate);
            return;
        }
        RankingScoreCandidate worst = minHeap.peek();
        if (worst == null) {
            return;
        }
        if (WORST_TO_BETTER.compare(worst, candidate) < 0) {
            minHeap.poll();
            minHeap.add(candidate);
        }
    }

    /**
     * rank 1(최고 점수)부터 오름차순으로 정렬된 행 목록을 만든다.
     */
    public List<RankingStagingRankRow> toSortedRankRows() {
        if (minHeap.isEmpty()) {
            return List.of();
        }
        List<RankingScoreCandidate> sorted = new ArrayList<>(minHeap);
        sorted.sort(
                Comparator.comparingDouble(RankingScoreCandidate::score).reversed()
                        .thenComparingLong(RankingScoreCandidate::productId)
        );
        List<RankingStagingRankRow> rows = new ArrayList<>(sorted.size());
        int rank = 1;
        for (RankingScoreCandidate c : sorted) {
            rows.add(new RankingStagingRankRow(rank++, c.productId(), BigDecimal.valueOf(c.score())));
        }
        return rows;
    }
}
