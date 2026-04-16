package com.loopers.batch.job.ranking.validator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MonthlyJobParametersValidatorTest {

    private final MonthlyJobParametersValidator validator = new MonthlyJobParametersValidator();

    @Test
    @DisplayName("유효한 year_month 포맷은 통과한다.")
    void passesForValidYearMonth() {
        JobParameters params = new JobParametersBuilder()
            .addString("year_month", "2026-04")
            .toJobParameters();
        assertThatCode(() -> validator.validate(params)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("year_month 파라미터 누락은 JobParametersInvalidException 을 던진다.")
    void throwsWhenMissing() {
        JobParameters params = new JobParametersBuilder()
            .addLong("run.id", 1L)
            .toJobParameters();
        assertThatThrownBy(() -> validator.validate(params))
            .isInstanceOf(JobParametersInvalidException.class)
            .hasMessageContaining("year_month");
    }

    @Test
    @DisplayName("잘못된 포맷(YYYYMM 등)은 JobParametersInvalidException 을 던진다.")
    void throwsWhenFormatInvalid() {
        JobParameters params = new JobParametersBuilder()
            .addString("year_month", "202604")
            .toJobParameters();
        assertThatThrownBy(() -> validator.validate(params))
            .isInstanceOf(JobParametersInvalidException.class);
    }

    @Test
    @DisplayName("존재하지 않는 월(13월)은 JobParametersInvalidException 을 던진다.")
    void throwsWhenMonthOutOfRange() {
        JobParameters params = new JobParametersBuilder()
            .addString("year_month", "2026-13")
            .toJobParameters();
        assertThatThrownBy(() -> validator.validate(params))
            .isInstanceOf(JobParametersInvalidException.class);
    }
}
