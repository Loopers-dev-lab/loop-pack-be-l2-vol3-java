package com.loopers.batch.job.rank.step;

import com.loopers.domain.rank.MvProductRankRepository;
import com.loopers.domain.rank.MvProductRankRow;
import com.loopers.domain.rank.RankPeriodType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class AtomicMvRankWriter implements ItemWriter<MvProductRankRow>, StepExecutionListener {

    private final MvProductRankRepository repository;
    private final RankPeriodType periodType;
    private final String periodKey;
    private final TransactionTemplate transactionTemplate;
    private final List<MvProductRankRow> accumulated = new ArrayList<>();

    public AtomicMvRankWriter(MvProductRankRepository repository,
                               RankPeriodType periodType,
                               String periodKey,
                               TransactionTemplate transactionTemplate) {
        this.repository = repository;
        this.periodType = periodType;
        this.periodKey = periodKey;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public void write(Chunk<? extends MvProductRankRow> chunk) {
        accumulated.addAll(chunk.getItems());
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        if (stepExecution.getStatus() != BatchStatus.COMPLETED || accumulated.isEmpty()) {
            log.info("MV 적재 스킵: status={}, accumulated={}", stepExecution.getStatus(), accumulated.size());
            return stepExecution.getExitStatus();
        }

        transactionTemplate.executeWithoutResult(status -> {
            repository.deleteByPeriodKey(periodType, periodKey);
            repository.batchInsert(periodType, accumulated);
            log.info("MV 원자 적재 완료: type={}, periodKey={}, rows={}", periodType, periodKey, accumulated.size());
        });

        return stepExecution.getExitStatus();
    }
}
