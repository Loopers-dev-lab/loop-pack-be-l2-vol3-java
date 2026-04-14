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
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class AtomicMvRankWriter implements ItemWriter<AggregatedScoreRow>, StepExecutionListener {

    private static final int ACCUMULATION_HARD_LIMIT = 10_000;

    private final MvProductRankRepository repository;
    private final RankPeriodType periodType;
    private final String periodKey;
    private final TransactionTemplate transactionTemplate;
    private final List<AggregatedScoreRow> accumulated = new ArrayList<>();

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
    public void write(Chunk<? extends AggregatedScoreRow> chunk) {
        if (accumulated.size() + chunk.size() > ACCUMULATION_HARD_LIMIT) {
            throw new IllegalStateException(
                    "AtomicMvRankWriter accumulated 상한 초과: current=" + accumulated.size()
                            + ", incoming=" + chunk.size() + ", limit=" + ACCUMULATION_HARD_LIMIT
                            + ". Reader LIMIT 또는 상한 재검토 필요"
            );
        }
        accumulated.addAll(chunk.getItems());
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        if (stepExecution.getStatus() != BatchStatus.COMPLETED || accumulated.isEmpty()) {
            log.info("MV 적재 스킵: status={}, accumulated={}", stepExecution.getStatus(), accumulated.size());
            return stepExecution.getExitStatus();
        }

        List<MvProductRankRow> ranked = assignRanks(accumulated);

        try {
            transactionTemplate.executeWithoutResult(status -> {
                repository.deleteByPeriodKey(periodType, periodKey);
                repository.batchInsert(periodType, ranked);
                log.info("MV 원자 적재 완료: type={}, periodKey={}, rows={}", periodType, periodKey, ranked.size());
            });
        } catch (RuntimeException e) {
            log.error("MV 원자 적재 실패: type={}, periodKey={}, rows={}", periodType, periodKey, ranked.size(), e);
            stepExecution.setStatus(BatchStatus.FAILED);
            stepExecution.addFailureException(e);
            return ExitStatus.FAILED;
        }

        return stepExecution.getExitStatus();
    }

    private List<MvProductRankRow> assignRanks(List<AggregatedScoreRow> rows) {
        List<AggregatedScoreRow> sorted = rows.stream()
                .sorted(Comparator.comparingDouble(AggregatedScoreRow::totalScore).reversed()
                        .thenComparingLong(AggregatedScoreRow::productDbId))
                .toList();

        AtomicInteger rank = new AtomicInteger(1);
        return sorted.stream()
                .map(row -> new MvProductRankRow(
                        periodKey,
                        rank.getAndIncrement(),
                        row.productDbId(),
                        row.totalScore(),
                        row.totalView(),
                        row.totalLike(),
                        row.totalOrder()
                ))
                .toList();
    }
}
