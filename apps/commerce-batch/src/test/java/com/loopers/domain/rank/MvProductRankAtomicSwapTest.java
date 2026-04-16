package com.loopers.domain.rank;

import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = "spring.batch.job.enabled=false")
@DisplayName("MV 원자 스왑 — 동시성 reader가 반쪽짜리 랭킹을 보지 않아야 한다")
class MvProductRankAtomicSwapTest {

    private static final String PERIOD_KEY = "2026W15";
    private static final int OLD_ROW_COUNT = 50;
    private static final int NEW_ROW_COUNT = 100;

    @Autowired
    private MvProductRankRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
        repository.batchInsert(RankPeriodType.WEEKLY, buildRows(PERIOD_KEY, OLD_ROW_COUNT, 1));
    }

    @Test
    @DisplayName("DELETE와 INSERT 사이 시점에 reader가 관찰해도 OLD 또는 NEW 상태만 본다 (빈 상태 불가)")
    void atomicSwap_readerNeverSeesEmpty() throws Exception {
        CountDownLatch writerInsideTx = new CountDownLatch(1);
        CountDownLatch readerObserved = new CountDownLatch(1);
        AtomicReference<Long> observedCountDuringTx = new AtomicReference<>();
        AtomicBoolean writerCompleted = new AtomicBoolean(false);

        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        Thread writer = new Thread(() -> {
            tx.executeWithoutResult(status -> {
                repository.deleteByPeriodKey(RankPeriodType.WEEKLY, PERIOD_KEY);
                writerInsideTx.countDown();
                try {
                    readerObserved.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
                repository.batchInsert(RankPeriodType.WEEKLY, buildRows(PERIOD_KEY, NEW_ROW_COUNT, 1000));
            });
            writerCompleted.set(true);
        }, "mv-atomic-writer");

        Thread reader = new Thread(() -> {
            try {
                assertThat(writerInsideTx.await(5, TimeUnit.SECONDS)).isTrue();
                long count = repository.countByPeriodKey(RankPeriodType.WEEKLY, PERIOD_KEY);
                observedCountDuringTx.set(count);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                readerObserved.countDown();
            }
        }, "mv-atomic-reader");

        writer.start();
        reader.start();
        reader.join(10_000);
        writer.join(10_000);

        assertThat(writerCompleted).as("writer 트랜잭션 커밋 완료").isTrue();
        assertThat(observedCountDuringTx.get())
                .as("DELETE 완료 후 INSERT 전 시점의 reader 관찰값 — 빈 상태(0)가 되면 원자성 깨짐")
                .isEqualTo(OLD_ROW_COUNT);

        long finalCount = repository.countByPeriodKey(RankPeriodType.WEEKLY, PERIOD_KEY);
        assertThat(finalCount).as("commit 후 NEW 상태로 전환").isEqualTo(NEW_ROW_COUNT);
    }

    @Test
    @DisplayName("writer 커밋 전 reader는 OLD 행의 내용까지 일관되게 본다 (gap 없이)")
    void atomicSwap_readerSeesConsistentOldSnapshot() throws Exception {
        CountDownLatch writerInsideTx = new CountDownLatch(1);
        CountDownLatch readerObserved = new CountDownLatch(1);
        AtomicReference<List<MvProductRankRow>> observed = new AtomicReference<>();

        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        Thread writer = new Thread(() -> {
            tx.executeWithoutResult(status -> {
                repository.deleteByPeriodKey(RankPeriodType.WEEKLY, PERIOD_KEY);
                writerInsideTx.countDown();
                try {
                    readerObserved.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
                repository.batchInsert(RankPeriodType.WEEKLY, buildRows(PERIOD_KEY, NEW_ROW_COUNT, 1000));
            });
        }, "mv-atomic-writer");

        Thread reader = new Thread(() -> {
            try {
                assertThat(writerInsideTx.await(5, TimeUnit.SECONDS)).isTrue();
                observed.set(repository.findByPeriodKey(RankPeriodType.WEEKLY, PERIOD_KEY, 0, 200));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                readerObserved.countDown();
            }
        }, "mv-atomic-reader");

        writer.start();
        reader.start();
        reader.join(10_000);
        writer.join(10_000);

        List<MvProductRankRow> snapshot = observed.get();
        assertThat(snapshot).as("reader snapshot").hasSize(OLD_ROW_COUNT);
        assertThat(snapshot.stream().allMatch(r -> r.refProductId() < 1000))
                .as("전부 OLD row (refProductId < 1000)이어야 함 — NEW row 섞이면 계약 위반")
                .isTrue();
    }

    private List<MvProductRankRow> buildRows(String periodKey, int count, long refIdBase) {
        List<MvProductRankRow> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rows.add(new MvProductRankRow(
                    periodKey,
                    i + 1,
                    refIdBase + i,
                    (double) (count - i),
                    10L,
                    5L,
                    BigDecimal.valueOf(100)
            ));
        }
        return rows;
    }
}
