package com.loopers.batch.job.rank.step;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Slf4j
public class ScoreCompletenessTasklet implements Tasklet {

    private static final String COUNT_DISTINCT_DATES_SQL =
            "SELECT COUNT(DISTINCT score_date) FROM mv_product_score_daily WHERE score_date BETWEEN ? AND ?";

    private final JdbcTemplate jdbcTemplate;
    private final LocalDate periodStart;
    private final LocalDate periodEnd;
    private final boolean failOnIncomplete;

    public ScoreCompletenessTasklet(JdbcTemplate jdbcTemplate,
                                    LocalDate periodStart,
                                    LocalDate periodEnd,
                                    boolean failOnIncomplete) {
        this.jdbcTemplate = jdbcTemplate;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.failOnIncomplete = failOnIncomplete;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        long expected = ChronoUnit.DAYS.between(periodStart, periodEnd) + 1;
        Long actual = jdbcTemplate.queryForObject(
                COUNT_DISTINCT_DATES_SQL, Long.class, periodStart, periodEnd
        );
        long actualDates = actual == null ? 0L : actual;

        if (actualDates >= expected) {
            log.info("Score 완결성 검증 통과: period=[{}, {}] dates={}/{}",
                    periodStart, periodEnd, actualDates, expected);
            return RepeatStatus.FINISHED;
        }

        String msg = String.format(
                "Score 완결성 부족: period=[%s, %s] dates=%d/%d (누락 %d일)",
                periodStart, periodEnd, actualDates, expected, expected - actualDates
        );
        if (failOnIncomplete) {
            log.error(msg);
            throw new IllegalStateException(msg);
        }
        log.warn("{} — failOnIncomplete=false로 진행", msg);
        return RepeatStatus.FINISHED;
    }
}
