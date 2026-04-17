package com.loopers.batch.job.ranking.step;

import com.loopers.batch.job.ranking.MonthlyRankingJobConfig;
import com.loopers.domain.ranking.RankingVersionManager;
import com.loopers.infrastructure.ranking.ProductRankMonthlyJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@StepScope
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = MonthlyRankingJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Component
public class MonthlyClearOldVersionTasklet implements Tasklet {

    private final ProductRankMonthlyJpaRepository monthlyRepository;
    private final RankingVersionManager versionManager;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        long nextVersion = versionManager.getNextMonthlyVersion();
        monthlyRepository.deleteByVersion(nextVersion);
        chunkContext.getStepContext()
            .getStepExecution()
            .getJobExecution()
            .getExecutionContext()
            .putLong("nextMonthlyVersion", nextVersion);
        log.info("월간 랭킹 이전 버전 정리 완료. nextVersion={}", nextVersion);
        return RepeatStatus.FINISHED;
    }
}
