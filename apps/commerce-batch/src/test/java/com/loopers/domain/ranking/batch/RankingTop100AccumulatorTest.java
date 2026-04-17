package com.loopers.domain.ranking.batch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

class RankingTop100AccumulatorTest {

    @Test
    @DisplayName("후보가 100개 이하면 모두 rank에 포함된다.")
    void toSortedRankRows_whenAtMost100_returnsAllOrdered() {
        RankingTop100Accumulator acc = new RankingTop100Accumulator();
        acc.accept(new RankingScoreCandidate(2L, 10.0d));
        acc.accept(new RankingScoreCandidate(1L, 20.0d));

        var rows = acc.toSortedRankRows();

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).rank()).isEqualTo(1);
        assertThat(rows.get(0).productId()).isEqualTo(1L);
        assertThat(rows.get(1).productId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("후보가 100개를 넘으면 점수 하위는 제외된다.")
    void toSortedRankRows_whenMoreThan100_keepsTop100ByScore() {
        RankingTop100Accumulator acc = new RankingTop100Accumulator();
        for (long i = 1L; i <= 100L; i++) {
            acc.accept(new RankingScoreCandidate(i, (double) i));
        }
        acc.accept(new RankingScoreCandidate(999L, 0.5d));

        var rows = acc.toSortedRankRows();

        assertThat(rows).hasSize(100);
        assertThat(rows.get(0).productId()).isEqualTo(100L);
        assertThat(rows.get(99).productId()).isEqualTo(1L);
        assertThat(rows.stream().anyMatch(r -> r.productId() == 999L)).isFalse();
    }

    @Test
    @DisplayName("동점이면 product_id가 작은 쪽이 더 높은 순위다.")
    void toSortedRankRows_whenScoreTie_ordersByProductIdAsc() {
        RankingTop100Accumulator acc = new RankingTop100Accumulator();
        acc.accept(new RankingScoreCandidate(20L, 5.0d));
        acc.accept(new RankingScoreCandidate(10L, 5.0d));

        var rows = acc.toSortedRankRows();

        assertThat(rows.get(0).productId()).isEqualTo(10L);
        assertThat(rows.get(1).productId()).isEqualTo(20L);
    }

    @Test
    @DisplayName("동점 상위 100 경계에서 id가 작은 상품이 남는다.")
    void accept_whenTieAtBoundary_prefersLowerProductId() {
        RankingTop100Accumulator acc = new RankingTop100Accumulator();
        LongStream.rangeClosed(1L, 100L).forEach(i -> acc.accept(new RankingScoreCandidate(i, 1.0d)));
        acc.accept(new RankingScoreCandidate(200L, 1.0d));

        var rows = acc.toSortedRankRows();

        assertThat(rows).hasSize(100);
        assertThat(rows.stream().anyMatch(r -> r.productId() == 200L)).isFalse();
    }
}
