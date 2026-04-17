package com.loopers.domain.ranking.batch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RankingBatchJobParametersTest {

    @Test
    @DisplayName("WEEKLY + yyyyWww 형식이면 통과한다.")
    void validate_whenWeeklyOk_shouldPass() {
        assertThatCode(() -> RankingBatchJobParameters.validate("WEEKLY", "2026W15"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("WEEKLY인데 주차 범위 밖이면 실패한다.")
    void validate_whenWeeklyWeekOutOfRange_shouldThrow() {
        assertThatThrownBy(() -> RankingBatchJobParameters.validate("WEEKLY", "2026W54"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("주차");
    }

    @Test
    @DisplayName("MONTHLY + yyyyMM 형식이면 통과한다.")
    void validate_whenMonthlyOk_shouldPass() {
        assertThatCode(() -> RankingBatchJobParameters.validate("MONTHLY", "202604"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("MONTHLY인데 월이 무효이면 실패한다.")
    void validate_whenMonthlyInvalidMonth_shouldThrow() {
        assertThatThrownBy(() -> RankingBatchJobParameters.validate("MONTHLY", "202613"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("월");
    }

    @Test
    @DisplayName("period가 잘못되면 실패한다.")
    void validate_whenPeriodInvalid_shouldThrow() {
        assertThatThrownBy(() -> RankingBatchJobParameters.validate("DAILY", "202604"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("periodKey가 비어 있으면 실패한다.")
    void validate_whenPeriodKeyBlank_shouldThrow() {
        assertThatThrownBy(() -> RankingBatchJobParameters.validate("WEEKLY", "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
