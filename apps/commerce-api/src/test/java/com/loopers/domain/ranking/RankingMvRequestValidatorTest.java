package com.loopers.domain.ranking;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RankingMvRequestValidatorTest {

    @Test
    @DisplayName("period만 있고 periodKey가 없으면 BAD_REQUEST")
    void validateMvPairPresent_whenOnlyPeriod_shouldThrow() {
        assertThatThrownBy(() -> RankingMvRequestValidator.validateMvPairPresent(true, false))
                .isInstanceOf(CoreException.class);
    }

    @Test
    @DisplayName("periodKey만 있으면 BAD_REQUEST")
    void validateMvPairPresent_whenOnlyPeriodKey_shouldThrow() {
        assertThatThrownBy(() -> RankingMvRequestValidator.validateMvPairPresent(false, true))
                .isInstanceOf(CoreException.class);
    }

    @Test
    @DisplayName("MV 요청과 date 동시 지정이면 BAD_REQUEST")
    void validateMutualExclusion_whenBoth_shouldThrow() {
        assertThatThrownBy(() -> RankingMvRequestValidator.validateMutualExclusion(
                Optional.of("20260408"), true))
                .isInstanceOf(CoreException.class);
    }

    @Test
    @DisplayName("유효한 주간 periodKey는 통과")
    void validatePeriodKey_weeklyOk() {
        assertThatCode(() -> RankingMvRequestValidator.validatePeriodKey(
                RankingMvPeriod.WEEKLY, "2026W15"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("유효한 월간 periodKey는 통과")
    void validatePeriodKey_monthlyOk() {
        assertThatCode(() -> RankingMvRequestValidator.validatePeriodKey(
                RankingMvPeriod.MONTHLY, "202604"))
                .doesNotThrowAnyException();
    }
}
