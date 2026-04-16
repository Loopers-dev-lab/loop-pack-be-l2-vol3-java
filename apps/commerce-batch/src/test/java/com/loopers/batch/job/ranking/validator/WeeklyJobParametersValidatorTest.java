package com.loopers.batch.job.ranking.validator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WeeklyJobParametersValidatorTest {

    private final WeeklyJobParametersValidator validator = new WeeklyJobParametersValidator();

    @Test
    @DisplayName("유효한 year_week 포맷은 통과한다.")
    void passesForValidYearWeek() {
        JobParameters params = new JobParametersBuilder()
            .addString("year_week", "2026-W15")
            .toJobParameters();
        assertThatCode(() -> validator.validate(params)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("year_week 파라미터 누락은 JobParametersInvalidException 을 던진다.")
    void throwsWhenMissing() {
        JobParameters params = new JobParametersBuilder()
            .addLong("run.id", 1L)
            .toJobParameters();
        assertThatThrownBy(() -> validator.validate(params))
            .isInstanceOf(JobParametersInvalidException.class)
            .hasMessageContaining("year_week");
    }

    @Test
    @DisplayName("잘못된 포맷은 JobParametersInvalidException 을 던진다.")
    void throwsWhenFormatInvalid() {
        JobParameters params = new JobParametersBuilder()
            .addString("year_week", "2026/15")
            .toJobParameters();
        assertThatThrownBy(() -> validator.validate(params))
            .isInstanceOf(JobParametersInvalidException.class);
    }

    @Test
    @DisplayName("존재하지 않는 주차(W54)는 JobParametersInvalidException 을 던진다.")
    void throwsWhenWeekOutOfRange() {
        JobParameters params = new JobParametersBuilder()
            .addString("year_week", "2026-W54")
            .toJobParameters();
        assertThatThrownBy(() -> validator.validate(params))
            .isInstanceOf(JobParametersInvalidException.class);
    }

    @Test
    @DisplayName("JobParameters 가 null 이면 JobParametersInvalidException 을 던진다.")
    void throwsWhenNull() {
        assertThatThrownBy(() -> validator.validate(null))
            .isInstanceOf(JobParametersInvalidException.class);
    }
}
