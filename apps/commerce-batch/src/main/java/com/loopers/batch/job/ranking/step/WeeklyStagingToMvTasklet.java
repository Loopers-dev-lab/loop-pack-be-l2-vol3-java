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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class WeeklyStagingToMvTasklet implements Tasklet {

    static final int TOP_N = 100;

    private static final String DELETE_SQL =
        "DELETE FROM mv_product_rank_weekly WHERE year_week = ?";

    // 정렬을 서브쿼리 한 곳(window ORDER BY)에서만 수행하고, 바깥에서 ranking_position 으로 TOP-N 컷.
    // "window ORDER BY 와 outer ORDER BY+LIMIT 이 일치해야만 순위 1..N 이 성립한다"는 숨은 불변식을 제거.
    private static final String INSERT_SQL =
        "INSERT INTO mv_product_rank_weekly "
            + "(year_week, product_id, ranking_position, score, created_at) "
            + "SELECT year_week, product_id, ranking_position, score, NOW(6) "
            + "FROM ("
            + "  SELECT ? AS year_week, product_id, score,"
            + "    ROW_NUMBER() OVER (ORDER BY score DESC, product_id ASC) AS ranking_position "
            + "  FROM mv_product_rank_weekly_staging "
            + "  WHERE year_week = ?"
            + ") ranked "
            + "WHERE ranking_position <= " + TOP_N;

    private final JdbcTemplate jdbcTemplate;

    @Value("#{jobParameters['year_week']}")
    private String yearWeek;

    /**
     * DELETE + INSERT 를 단일 트랜잭션에 묶는다. StepBuilder 가 tasklet 에 transactionManager 를 전달하여
     * 기본적으로 한 트랜잭션으로 실행되지만, 재사용 / 설정 변경에 안전하도록 @Transactional 을 명시한다.
     * INSERT 가 실패하면 DELETE 도 롤백되어 "이전 주차 MV 만 사라지는" 상태가 되지 않는다.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        int deleted = jdbcTemplate.update(DELETE_SQL, yearWeek);
        int inserted = jdbcTemplate.update(INSERT_SQL, yearWeek, yearWeek);
        log.info("[WeeklyRankingJob] promoted staging to MV. yearWeek={}, deleted={}, inserted={}",
            yearWeek, deleted, inserted);
        return RepeatStatus.FINISHED;
    }
}
