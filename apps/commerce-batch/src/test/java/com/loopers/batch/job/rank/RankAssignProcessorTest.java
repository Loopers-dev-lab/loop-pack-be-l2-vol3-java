package com.loopers.batch.job.rank;

import com.loopers.batch.job.rank.step.AggregatedScoreRow;
import com.loopers.batch.job.rank.step.RankAssignProcessor;
import com.loopers.domain.rank.MvProductRankRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RankAssignProcessorTest {

    @DisplayName("순차적으로 rank 번호를 부여한다")
    @Test
    void assignsSequentialRank() {
        // arrange
        RankAssignProcessor processor = new RankAssignProcessor("2026W15");
        AggregatedScoreRow row1 = new AggregatedScoreRow(42L, 5040.0, 700, 350, BigDecimal.valueOf(7000));
        AggregatedScoreRow row2 = new AggregatedScoreRow(43L, 3600.0, 500, 250, BigDecimal.valueOf(5000));
        AggregatedScoreRow row3 = new AggregatedScoreRow(44L, 1800.0, 250, 125, BigDecimal.valueOf(2500));

        // act
        MvProductRankRow result1 = processor.process(row1);
        MvProductRankRow result2 = processor.process(row2);
        MvProductRankRow result3 = processor.process(row3);

        // assert
        assertThat(result1.rankNo()).isEqualTo(1);
        assertThat(result2.rankNo()).isEqualTo(2);
        assertThat(result3.rankNo()).isEqualTo(3);
    }

    @DisplayName("periodKey가 올바르게 전달된다")
    @Test
    void periodKeyPropagated() {
        // arrange
        RankAssignProcessor processor = new RankAssignProcessor("202604");
        AggregatedScoreRow row = new AggregatedScoreRow(42L, 5040.0, 700, 350, BigDecimal.valueOf(7000));

        // act
        MvProductRankRow result = processor.process(row);

        // assert
        assertThat(result.periodKey()).isEqualTo("202604");
        assertThat(result.refProductId()).isEqualTo(42L);
        assertThat(result.score()).isEqualTo(5040.0);
    }
}
