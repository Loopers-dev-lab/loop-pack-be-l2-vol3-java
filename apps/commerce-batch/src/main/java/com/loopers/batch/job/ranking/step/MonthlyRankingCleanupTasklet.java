package com.loopers.batch.job.ranking.step;

import com.loopers.batch.job.ranking.MonthlyRankingJobConfig;
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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
@StepScope
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = MonthlyRankingJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Component
public class MonthlyRankingCleanupTasklet implements Tasklet {

    private final MvProductRankMonthlyJpaRepository repository;

    @Value("#{jobParameters['monthStartDate']}")
    private String monthStartDate;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        LocalDate startDate = LocalDate.parse(monthStartDate, DateTimeFormatter.BASIC_ISO_DATE);
        repository.deleteByMonthStartDate(startDate);
        log.info("[MonthlyCleanup] deleted monthStartDate={}", startDate);
        return RepeatStatus.FINISHED;
    }
}
