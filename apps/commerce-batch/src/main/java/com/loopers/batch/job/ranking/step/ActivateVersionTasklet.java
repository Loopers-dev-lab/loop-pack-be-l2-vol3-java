package com.loopers.batch.job.ranking.step;

import com.loopers.batch.job.ranking.WeeklyRankingJobConfig;
import com.loopers.domain.ranking.RankingVersionManager;
import com.loopers.infrastructure.ranking.ProductRankWeeklyJpaRepository;
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
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = WeeklyRankingJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Component
public class ActivateVersionTasklet implements Tasklet {

    private final RankingVersionManager versionManager;
    private final ProductRankWeeklyJpaRepository weeklyRepository;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        long nextVersion = chunkContext.getStepContext()
            .getStepExecution()
            .getJobExecution()
            .getExecutionContext()
            .getLong("nextWeeklyVersion");

        long oldVersion = versionManager.getCurrentWeeklyVersion();
        versionManager.activateWeeklyVersion(nextVersion);

        if (oldVersion > 0) {
            weeklyRepository.deleteByVersion(oldVersion);
        }

        log.info("주간 랭킹 버전 교체 완료: {} -> {}", oldVersion, nextVersion);
        return RepeatStatus.FINISHED;
    }
}
