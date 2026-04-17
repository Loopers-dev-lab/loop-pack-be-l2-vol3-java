package com.loopers.domain.ranking.batch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RankingStagingSnapshotValidatorTest {

    @Test
    @DisplayName("빈 스냅샷은 통과한다.")
    void validate_whenEmpty_doesNotThrow() {
        assertThatCode(() -> RankingStagingSnapshotValidator.validateOrThrow(List.of()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("rank가 1부터 연속이면 통과한다.")
    void validate_whenRanksContiguous_doesNotThrow() {
        var rows = List.of(
                new RankingStagingRankRow(1, 10L, BigDecimal.ONE),
                new RankingStagingRankRow(2, 20L, BigDecimal.TEN)
        );
        assertThatCode(() -> RankingStagingSnapshotValidator.validateOrThrow(rows))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("행이 100개를 넘으면 실패한다.")
    void validate_whenMoreThan100_shouldFail() {
        var rows = new ArrayList<RankingStagingRankRow>();
        for (int i = 1; i <= 101; i++) {
            rows.add(new RankingStagingRankRow(i, (long) i, BigDecimal.valueOf(i)));
        }
        assertThatThrownBy(() -> RankingStagingSnapshotValidator.validateOrThrow(rows))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("100");
    }

    @Test
    @DisplayName("rank에 공백이 있으면 실패한다.")
    void validate_whenRankGap_shouldFail() {
        var rows = List.of(
                new RankingStagingRankRow(1, 1L, BigDecimal.ONE),
                new RankingStagingRankRow(3, 2L, BigDecimal.TEN)
        );
        assertThatThrownBy(() -> RankingStagingSnapshotValidator.validateOrThrow(rows))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("연속");
    }

    @Test
    @DisplayName("product_id 중복이면 실패한다.")
    void validate_whenDuplicateProduct_shouldFail() {
        var rows = List.of(
                new RankingStagingRankRow(1, 1L, BigDecimal.ONE),
                new RankingStagingRankRow(2, 1L, BigDecimal.TEN)
        );
        assertThatThrownBy(() -> RankingStagingSnapshotValidator.validateOrThrow(rows))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("중복");
    }
}
