package com.loopers.batch.job.ranking.step.purge;

import com.loopers.batch.job.ranking.param.RankingJobParametersListener;
import com.loopers.domain.ranking.mv.MvProductRankLast7dRepository;
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
 * Step 4a — 현재 anchorDate 의 LAST_7D MV row 를 사전 DELETE 한다.
 * Step 5b 의 INSERT 가 돌기 전에 "MV 는 비어있음" 상태를 보장하여,
 * MV 가 거쳐가는 상태를 "비어있음 → 확정된 TOP 100" 두 가지로만 제한한다 (중간 상태 불가시성).
 */
@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class PurgeLast7dMvTasklet implements Tasklet {

    private static final DateTimeFormatter KEY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final MvProductRankLast7dRepository repository;

    @Value("#{jobExecutionContext['" + RankingJobParametersListener.CTX_ANCHOR_DATE_KEY + "']}")
    private String anchorDateKey;

    @Override
    @Transactional
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        LocalDate anchorDate = LocalDate.parse(anchorDateKey, KEY_FORMAT);
        int deleted = repository.deleteByAnchorDate(anchorDate);

        log.info("[STEP=purgeLast7dMvStep] anchorDate={} deleted={}", anchorDate, deleted);

        contribution.incrementWriteCount(deleted);
        return RepeatStatus.FINISHED;
    }
}
