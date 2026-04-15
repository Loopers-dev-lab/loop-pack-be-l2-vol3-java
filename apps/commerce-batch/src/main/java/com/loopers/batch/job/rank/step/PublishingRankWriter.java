package com.loopers.batch.job.rank.step;

import com.loopers.domain.rank.MvProductRankPublicationRepository;
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
public class PublishingRankWriter implements ItemWriter<AggregatedScoreRow>, StepExecutionListener {

    private static final int ACCUMULATION_HARD_LIMIT = 10_000;

    private final MvProductRankRepository rankRepository;
    private final MvProductRankPublicationRepository publicationRepository;
    private final RankPeriodType periodType;
    private final String periodKey;
    private final TransactionTemplate insertTx;
    private final TransactionTemplate publishTx;
    private final List<AggregatedScoreRow> accumulated = new ArrayList<>();

    public PublishingRankWriter(MvProductRankRepository rankRepository,
                                 MvProductRankPublicationRepository publicationRepository,
                                 RankPeriodType periodType,
                                 String periodKey,
                                 TransactionTemplate insertTx,
                                 TransactionTemplate publishTx) {
        this.rankRepository = rankRepository;
        this.publicationRepository = publicationRepository;
        this.periodType = periodType;
        this.periodKey = periodKey;
        this.insertTx = insertTx;
        this.publishTx = publishTx;
    }

    @Override
    public void write(Chunk<? extends AggregatedScoreRow> chunk) {
        if (accumulated.size() + chunk.size() > ACCUMULATION_HARD_LIMIT) {
            throw new IllegalStateException(
                    "PublishingRankWriter accumulated 상한 초과: current=" + accumulated.size()
                            + ", incoming=" + chunk.size() + ", limit=" + ACCUMULATION_HARD_LIMIT
            );
        }
        accumulated.addAll(chunk.getItems());
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        if (stepExecution.getStatus() != BatchStatus.COMPLETED || accumulated.isEmpty()) {
            log.info("MV publish 스킵: status={}, accumulated={}", stepExecution.getStatus(), accumulated.size());
            return stepExecution.getExitStatus();
        }

        long publishStartNanos = System.nanoTime();
        try {
            Long myVersion = insertTx.execute(status -> {
                long newVersion = publicationRepository.bumpNextVersion(periodType, periodKey);
                List<MvProductRankRow> ranked = assignRanks(accumulated, newVersion);
                rankRepository.batchInsert(periodType, ranked);
                return newVersion;
            });

            if (myVersion == null) {
                throw new IllegalStateException("insertTx returned null version");
            }

            boolean published = Boolean.TRUE.equals(publishTx.execute(
                    status -> publicationRepository.casPublishIfGreater(periodType, periodKey, myVersion)
            ));

            long durationMs = (System.nanoTime() - publishStartNanos) / 1_000_000L;
            log.info("MV publish 완료: type={}, periodKey={}, version={}, published={}, rows={}, durationMs={}",
                    periodType, periodKey, myVersion, published, accumulated.size(), durationMs);
            stepExecution.getExecutionContext().putLong("mvPublishDurationMs", durationMs);
        } catch (RuntimeException e) {
            long durationMs = (System.nanoTime() - publishStartNanos) / 1_000_000L;
            log.error("MV publish 실패: type={}, periodKey={}, rows={}, elapsedMs={}",
                    periodType, periodKey, accumulated.size(), durationMs, e);
            stepExecution.setStatus(BatchStatus.FAILED);
            stepExecution.addFailureException(e);
            return ExitStatus.FAILED;
        }

        return stepExecution.getExitStatus();
    }

    private List<MvProductRankRow> assignRanks(List<AggregatedScoreRow> rows, long version) {
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
                        row.totalOrder(),
                        version
                ))
                .toList();
    }
}
