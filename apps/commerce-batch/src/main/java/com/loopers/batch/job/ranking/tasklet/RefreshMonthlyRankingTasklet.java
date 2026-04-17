package com.loopers.batch.job.ranking.tasklet;

import com.loopers.batch.domain.ranking.AggregatedRankingRow;
import com.loopers.batch.domain.ranking.RankingPeriodKeyFactory;
import com.loopers.batch.infrastructure.ranking.entity.MonthlyRankingEntity;
import com.loopers.batch.infrastructure.ranking.repository.MonthlyRankingJpaRepository;
import com.loopers.batch.job.ranking.MonthlyRankingJobConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@StepScope
@RequiredArgsConstructor
@Component
public class RefreshMonthlyRankingTasklet implements Tasklet {

    private final JdbcTemplate jdbcTemplate;
    private final MonthlyRankingJpaRepository repository;

    @Value("#{jobParameters['targetDate']}")
    private String targetDate;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        LocalDate date = LocalDate.parse(targetDate, DateTimeFormatter.BASIC_ISO_DATE);
        LocalDate startDate = RankingPeriodKeyFactory.monthlyStart(date);
        LocalDate endDate = RankingPeriodKeyFactory.monthlyEnd(date);
        String periodKey = RankingPeriodKeyFactory.toMonthlyKey(date);

        List<AggregatedRankingRow> rows = jdbcTemplate.query(
                """
                        select
                            product_id,
                            sum(view_count) as view_count,
                            sum(like_count) as like_count,
                            sum(order_count) as order_count,
                            sum(score) as score
                        from product_metrics_daily
                        where metric_date between ? and ?
                        group by product_id
                        order by score desc, product_id asc
                        limit %d
                        """.formatted(MonthlyRankingJobConfig.TOP_N),
                (rs, rowNum) -> new AggregatedRankingRow(
                        rowNum + 1,
                        rs.getLong("product_id"),
                        rs.getLong("view_count"),
                        rs.getLong("like_count"),
                        rs.getLong("order_count"),
                        rs.getDouble("score")
                ),
                startDate,
                endDate
        );

        repository.deleteByPeriodKey(periodKey);
        if (!rows.isEmpty()) {
            repository.saveAll(rows.stream()
                    .map(row -> MonthlyRankingEntity.of(periodKey, row))
                    .toList());
        }
        return RepeatStatus.FINISHED;
    }
}
