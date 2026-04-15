package com.loopers.batch.job.rank.step;

import com.loopers.domain.rank.MvProductRankPublicationRepository;
import com.loopers.domain.rank.MvProductRankRepository;
import com.loopers.domain.rank.MvProductRankRow;
import com.loopers.domain.rank.RankPeriodType;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class PublishingRankWriter implements ItemWriter<AggregatedScoreRow>, StepExecutionListener {

    private static final String CTX_KEY_VERSION = "publishingWriter.version";
    private static final String CTX_KEY_RANK = "publishingWriter.rankCursor";
    private static final String CTX_KEY_WRITTEN = "publishingWriter.written";

    private final MvProductRankRepository rankRepository;
    private final MvProductRankPublicationRepository publicationRepository;
    private final RankPeriodType periodType;
    private final String periodKey;
    private final TransactionTemplate insertTx;
    private final TransactionTemplate publishTx;
    private final MeterRegistry meterRegistry;
    private final AtomicInteger rankCounter = new AtomicInteger(0);

    private volatile long myVersion = -1L;
    private volatile boolean versionAssigned = false;
    private volatile long totalWritten = 0L;
    private volatile ExecutionContext executionContext;

    public PublishingRankWriter(MvProductRankRepository rankRepository,
                                 MvProductRankPublicationRepository publicationRepository,
                                 RankPeriodType periodType,
                                 String periodKey,
                                 TransactionTemplate insertTx,
                                 TransactionTemplate publishTx,
                                 MeterRegistry meterRegistry) {
        this.rankRepository = rankRepository;
        this.publicationRepository = publicationRepository;
        this.periodType = periodType;
        this.periodKey = periodKey;
        this.insertTx = insertTx;
        this.publishTx = publishTx;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        this.executionContext = stepExecution.getExecutionContext();
        if (executionContext.containsKey(CTX_KEY_VERSION)) {
            myVersion = executionContext.getLong(CTX_KEY_VERSION);
            versionAssigned = true;
            rankCounter.set((int) executionContext.getLong(CTX_KEY_RANK, 0L));
            totalWritten = executionContext.getLong(CTX_KEY_WRITTEN, 0L);
            log.info("PublishingRankWriter restart 복원: version={} rankCursor={} written={}",
                    myVersion, rankCounter.get(), totalWritten);
        }
    }

    @Override
    public void write(Chunk<? extends AggregatedScoreRow> chunk) {
        if (chunk.isEmpty()) {
            return;
        }
        Long version = ensureVersionAssigned();
        List<MvProductRankRow> rows = assignRanks(chunk.getItems(), version);
        insertTx.executeWithoutResult(status -> rankRepository.batchInsert(periodType, rows));
        totalWritten += rows.size();
        if (executionContext != null) {
            executionContext.putLong(CTX_KEY_RANK, rankCounter.get());
            executionContext.putLong(CTX_KEY_WRITTEN, totalWritten);
        }
    }

    private long ensureVersionAssigned() {
        if (versionAssigned) {
            return myVersion;
        }
        Timer.Sample sample = meterRegistry == null ? null : Timer.start(meterRegistry);
        myVersion = publicationRepository.bumpNextVersion(periodType, periodKey);
        if (sample != null) {
            sample.stop(timer("batch.rank.bump", periodType));
        }
        versionAssigned = true;
        if (executionContext != null) {
            executionContext.putLong(CTX_KEY_VERSION, myVersion);
        }
        log.info("PublishingRankWriter version 획득: type={} periodKey={} version={}",
                periodType, periodKey, myVersion);
        return myVersion;
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        if (stepExecution.getStatus() != BatchStatus.COMPLETED) {
            log.info("MV publish 스킵: status={}, written={}", stepExecution.getStatus(), totalWritten);
            return stepExecution.getExitStatus();
        }
        if (!versionAssigned) {
            log.info("MV publish 스킵: 입력 없음 (versionAssigned=false)");
            return stepExecution.getExitStatus();
        }
        try {
            Timer.Sample sample = meterRegistry == null ? null : Timer.start(meterRegistry);
            long startNs = System.nanoTime();
            boolean published = Boolean.TRUE.equals(publishTx.execute(
                    status -> publicationRepository.casPublishIfGreater(periodType, periodKey, myVersion)
            ));
            long casDurationNs = sample == null
                    ? (System.nanoTime() - startNs)
                    : sample.stop(timer("batch.rank.cas", periodType));
            long durationMs = TimeUnit.NANOSECONDS.toMillis(casDurationNs);
            log.info("MV publish 완료: type={}, periodKey={}, version={}, published={}, written={}, casDurationMs={}",
                    periodType, periodKey, myVersion, published, totalWritten, durationMs);
            stepExecution.getExecutionContext().putLong("mvPublishVersion", myVersion);
            stepExecution.getExecutionContext().putLong("mvCasDurationMs", durationMs);
        } catch (RuntimeException e) {
            log.error("MV publish CAS 실패: type={}, periodKey={}, version={}, written={}",
                    periodType, periodKey, myVersion, totalWritten, e);
            stepExecution.setStatus(BatchStatus.FAILED);
            stepExecution.addFailureException(e);
            return ExitStatus.FAILED;
        }
        return stepExecution.getExitStatus();
    }

    private List<MvProductRankRow> assignRanks(List<? extends AggregatedScoreRow> items, long version) {
        List<MvProductRankRow> out = new ArrayList<>(items.size());
        for (AggregatedScoreRow item : items) {
            int rank = rankCounter.incrementAndGet();
            BigDecimal orderAmount = item.totalOrder() == null ? BigDecimal.ZERO : item.totalOrder();
            out.add(new MvProductRankRow(
                    periodKey, rank, item.productDbId(),
                    item.totalScore(), item.totalView(), item.totalLike(), orderAmount,
                    version
            ));
        }
        return out;
    }

    private Timer timer(String name, RankPeriodType type) {
        return Timer.builder(name)
                .tag("period_type", type.name())
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
    }
}
