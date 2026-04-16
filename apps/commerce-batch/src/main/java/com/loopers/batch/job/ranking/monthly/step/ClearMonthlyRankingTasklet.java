package com.loopers.batch.job.ranking.monthly.step;

import com.loopers.batch.job.ranking.monthly.MonthlyRankingJobConfig;
import com.loopers.infrastructure.ranking.MvProductRankMonthlyJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@StepScope
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = MonthlyRankingJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Component
public class ClearMonthlyRankingTasklet implements Tasklet {

    private final MvProductRankMonthlyJpaRepository monthlyJpaRepository;

    @Value("#{jobParameters['requestDate']}")
    private String requestDate;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        String yearMonth = computeYearMonth(requestDate);
        log.info("월간 랭킹 MV 초기화: yearMonth={}", yearMonth);
        monthlyJpaRepository.deleteByYearMonth(yearMonth);
        return RepeatStatus.FINISHED;
    }

    /** "yyyyMMdd" → "yyyyMM". 예: "20260412" → "202604" */
    public static String computeYearMonth(String dateStr) {
        return dateStr.substring(0, 6);
    }
}
