package com.loopers.batch.job.ranking.step.truncate;

import com.loopers.batch.job.ranking.param.RankingJobParametersListener;
import com.loopers.domain.ranking.staging.StagingRankingAggregationRepository;
import com.loopers.domain.ranking.staging.StagingRankingScoredRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Step 0 — 현재 anchor 의 스테이징만 초기화한다 (전체 TRUNCATE 아님).
 * 재실행 시 이전 시도의 잔재를 제거하여 멱등성 확보.
 */
@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class TruncateStagingTasklet implements Tasklet {

    private final StagingRankingAggregationRepository aggregationRepository;
    private final StagingRankingScoredRepository scoredRepository;

    @Value("#{jobExecutionContext['" + RankingJobParametersListener.CTX_ANCHOR_DATE_KEY + "']}")
    private String anchorDateKey;

    @Override
    @Transactional
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        int aggregationDeleted = aggregationRepository.deleteByPeriodKey(anchorDateKey);
        int scoredDeleted = scoredRepository.deleteByPeriodKey(anchorDateKey);

        log.info(
                "[STEP=truncateStagingStep] anchorDateKey={} aggregationDeleted={} scoredDeleted={}",
                anchorDateKey, aggregationDeleted, scoredDeleted
        );

        contribution.incrementWriteCount(aggregationDeleted + scoredDeleted);
        return RepeatStatus.FINISHED;
    }
}
