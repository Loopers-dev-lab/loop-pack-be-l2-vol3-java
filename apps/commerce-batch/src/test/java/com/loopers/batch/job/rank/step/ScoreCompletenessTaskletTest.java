package com.loopers.batch.job.rank.step;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("ScoreCompletenessTasklet")
class ScoreCompletenessTaskletTest {

    private JdbcTemplate jdbcTemplate;
    private final LocalDate start = LocalDate.of(2026, 4, 7);
    private final LocalDate end = LocalDate.of(2026, 4, 13);

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
    }

    @Test
    @DisplayName("기간 내 모든 날짜의 score 데이터가 존재하면 FINISHED 반환")
    void allDatesPresent_returnsFinished() {
        when(jdbcTemplate.queryForObject(any(String.class), eq(Long.class), eq(start), eq(end)))
                .thenReturn(7L);
        ScoreCompletenessTasklet tasklet = new ScoreCompletenessTasklet(jdbcTemplate, start, end, true);

        RepeatStatus result = tasklet.execute(null, null);

        assertThat(result).isEqualTo(RepeatStatus.FINISHED);
    }

    @Test
    @DisplayName("일부 날짜 누락 + failOnIncomplete=true → IllegalStateException")
    void partialDatesMissing_failModeOn_throws() {
        when(jdbcTemplate.queryForObject(any(String.class), eq(Long.class), eq(start), eq(end)))
                .thenReturn(5L);
        ScoreCompletenessTasklet tasklet = new ScoreCompletenessTasklet(jdbcTemplate, start, end, true);

        assertThatThrownBy(() -> tasklet.execute(null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dates=5/7")
                .hasMessageContaining("누락 2일");
    }

    @Test
    @DisplayName("일부 날짜 누락 + failOnIncomplete=false → WARN 후 FINISHED")
    void partialDatesMissing_failModeOff_returnsFinished() {
        when(jdbcTemplate.queryForObject(any(String.class), eq(Long.class), eq(start), eq(end)))
                .thenReturn(5L);
        ScoreCompletenessTasklet tasklet = new ScoreCompletenessTasklet(jdbcTemplate, start, end, false);

        RepeatStatus result = tasklet.execute(null, null);

        assertThat(result).isEqualTo(RepeatStatus.FINISHED);
    }

    @Test
    @DisplayName("score 데이터 0건 + failOnIncomplete=true → 즉시 실패")
    void noDataAtAll_failModeOn_throws() {
        when(jdbcTemplate.queryForObject(any(String.class), eq(Long.class), eq(start), eq(end)))
                .thenReturn(0L);
        ScoreCompletenessTasklet tasklet = new ScoreCompletenessTasklet(jdbcTemplate, start, end, true);

        assertThatThrownBy(() -> tasklet.execute(null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dates=0/7");
    }

    @Test
    @DisplayName("queryForObject가 null 반환 시에도 안전하게 0으로 처리")
    void nullCount_treatedAsZero() {
        when(jdbcTemplate.queryForObject(any(String.class), eq(Long.class), eq(start), eq(end)))
                .thenReturn(null);
        ScoreCompletenessTasklet tasklet = new ScoreCompletenessTasklet(jdbcTemplate, start, end, true);

        assertThatThrownBy(() -> tasklet.execute(null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dates=0/7");
    }
}
