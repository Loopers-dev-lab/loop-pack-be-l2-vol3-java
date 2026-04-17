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
public class MonthlyActivateVersionTasklet implements Tasklet {

    private final RankingVersionManager versionManager;
    private final ProductRankMonthlyJpaRepository monthlyRepository;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        long nextVersion = chunkContext.getStepContext()
            .getStepExecution()
            .getJobExecution()
            .getExecutionContext()
            .getLong("nextMonthlyVersion");

        long oldVersion = versionManager.getCurrentMonthlyVersion();
        versionManager.activateMonthlyVersion(nextVersion);

        if (oldVersion > 0) {
            monthlyRepository.deleteByVersion(oldVersion);
        }

        log.info("월간 랭킹 버전 교체 완료: {} -> {}", oldVersion, nextVersion);
        return RepeatStatus.FINISHED;
    }
}
