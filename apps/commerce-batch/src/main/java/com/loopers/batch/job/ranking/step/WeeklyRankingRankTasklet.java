package com.loopers.batch.job.ranking.step;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@StepScope
@Component
@RequiredArgsConstructor
public class WeeklyRankingRankTasklet implements Tasklet {

    private static final int TOP_N = 100;

    @Value("#{jobParameters['targetDate']}")
    private String targetDate;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        String yearWeek = WeeklyRankingClearTasklet.toYearWeek(targetDate);
        log.info("[WeeklyRankingRankTasklet] 랭킹 순위 부여 시작: yearWeek={}", yearWeek);

        jdbcTemplate.update(
            """
            UPDATE mv_product_rank_weekly w
            JOIN (
                SELECT id, ROW_NUMBER() OVER (ORDER BY score DESC) AS rn
                FROM mv_product_rank_weekly
                WHERE period_week = ?
            ) ranked ON w.id = ranked.id
            SET w.rank_position = ranked.rn
            WHERE w.period_week = ?
            """,
            yearWeek, yearWeek
        );

        int deleted = jdbcTemplate.update(
            "DELETE FROM mv_product_rank_weekly WHERE period_week = ? AND rank_position > ?",
            yearWeek, TOP_N
        );
        log.info("[WeeklyRankingRankTasklet] TOP {} 초과 삭제: {}건", TOP_N, deleted);
        return RepeatStatus.FINISHED;
    }
}
