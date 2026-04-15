package com.loopers.batch.job.ranking.step.purge;

import com.loopers.batch.job.ranking.param.RankingJobParametersListener;
import com.loopers.domain.ranking.mv.MvProductRankLast30dRepository;
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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Step 4b — 현재 anchorDate 의 LAST_30D MV row 를 사전 DELETE.
 * {@link PurgeLast7dMvTasklet} 주석 참고.
 */
@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class PurgeLast30dMvTasklet implements Tasklet {

    private static final DateTimeFormatter KEY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final MvProductRankLast30dRepository repository;

    @Value("#{jobExecutionContext['" + RankingJobParametersListener.CTX_ANCHOR_DATE_KEY + "']}")
    private String anchorDateKey;

    @Override
    @Transactional
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        LocalDate anchorDate = LocalDate.parse(anchorDateKey, KEY_FORMAT);
        int deleted = repository.deleteByAnchorDate(anchorDate);

        log.info("[STEP=purgeLast30dMvStep] anchorDate={} deleted={}", anchorDate, deleted);

        contribution.incrementWriteCount(deleted);
        return RepeatStatus.FINISHED;
    }
}
