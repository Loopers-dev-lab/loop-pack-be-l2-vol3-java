package com.loopers.domain.ranking.batch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RankingMvScoreCalculatorTest {

    @Test
    @DisplayName("조회·좋아요·판매에 기본 가중치(0.1,0.2,0.6)를 곱해 합산한다.")
    void score_withUnitCounts_matchesWeightedSum() {
        double s = RankingMvScoreCalculator.score(1L, 1L, 1L);
        assertThat(s).isEqualTo(0.1d + 0.2d + 0.6d);
    }

    @Test
    @DisplayName("음수 카운트면 IllegalArgumentException이다.")
    void score_withNegativeCount_shouldFail() {
        assertThatThrownBy(() -> RankingMvScoreCalculator.score(-1L, 0L, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
