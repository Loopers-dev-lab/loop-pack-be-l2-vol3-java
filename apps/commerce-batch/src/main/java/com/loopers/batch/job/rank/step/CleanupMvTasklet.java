package com.loopers.batch.job.rank.step;

import com.loopers.domain.rank.MvProductRankRepository;
import com.loopers.domain.rank.RankPeriodType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

@Slf4j
@RequiredArgsConstructor
public class CleanupMvTasklet implements Tasklet {

    private final MvProductRankRepository mvProductRankRepository;
    private final RankPeriodType periodType;
    private final String periodKey;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        log.info("MV cleanup: type={}, periodKey={}", periodType, periodKey);
        mvProductRankRepository.deleteByPeriodKey(periodType, periodKey);
        return RepeatStatus.FINISHED;
    }
}
