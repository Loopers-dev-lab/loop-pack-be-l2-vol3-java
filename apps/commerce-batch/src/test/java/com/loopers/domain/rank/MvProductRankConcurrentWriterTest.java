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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = "spring.batch.job.enabled=false")
@DisplayName("MV 동시 writer — 같은 periodKey로 2 writer 동시 실행 시 최종 상태는 한 쪽만")
class MvProductRankConcurrentWriterTest {

    private static final String PERIOD_KEY = "2026W15";

    @Autowired
    private MvProductRankRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("동일 periodKey로 두 writer 동시 실행 — 한 쪽 성공 / 다른 쪽 deadlock (현재 운영 위험 노출)")
    void concurrentWriters_oneSucceedsOneDeadlocks() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        int setASize = 50;
        int setBSize = 100;
        long setAIdBase = 1L;
        long setBIdBase = 1000L;

        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicLong writerAElapsed = new AtomicLong();
        AtomicLong writerBElapsed = new AtomicLong();

        Runnable writerA = () -> {
            long start = System.nanoTime();
            try {
                tx.executeWithoutResult(status -> {
                    repository.deleteByPeriodKey(RankPeriodType.WEEKLY, PERIOD_KEY);
                    repository.batchInsert(RankPeriodType.WEEKLY, buildRows(setASize, setAIdBase));
                });
            } finally {
                writerAElapsed.set((System.nanoTime() - start) / 1_000_000L);
            }
        };
        Runnable writerB = () -> {
            long start = System.nanoTime();
            try {
                tx.executeWithoutResult(status -> {
                    repository.deleteByPeriodKey(RankPeriodType.WEEKLY, PERIOD_KEY);
                    repository.batchInsert(RankPeriodType.WEEKLY, buildRows(setBSize, setBIdBase));
                });
            } finally {
                writerBElapsed.set((System.nanoTime() - start) / 1_000_000L);
            }
        };

        Future<?> fa = executor.submit(writerA);
        Future<?> fb = executor.submit(writerB);

        int failures = 0;
        int successes = 0;
        Throwable deadlockCause = null;
        for (Future<?> f : List.of(fa, fb)) {
            try {
                f.get(15, TimeUnit.SECONDS);
                successes++;
            } catch (ExecutionException ee) {
                failures++;
                deadlockCause = ee.getCause();
            }
        }
        executor.shutdown();

        assertThat(successes)
                .as("현재 구현은 동시 writer 시 한 쪽만 성공 — 재진입 가드 없음")
                .isEqualTo(1);
        assertThat(failures)
                .as("다른 쪽은 MySQL deadlock으로 실패 (운영 위험: 중복 Job 기동 방지 필요)")
                .isEqualTo(1);
        assertThat(deadlockCause)
                .isInstanceOf(org.springframework.dao.CannotAcquireLockException.class);

        long finalCount = repository.countByPeriodKey(RankPeriodType.WEEKLY, PERIOD_KEY);
        List<MvProductRankRow> finalRows = repository.findByPeriodKey(
                RankPeriodType.WEEKLY, PERIOD_KEY, 0, 200
        );

        assertThat(finalCount).as("최종 count는 50 또는 100 (성공한 쪽 set 전체)").isIn(50L, 100L);

        boolean allFromA = !finalRows.isEmpty() && finalRows.stream()
                .allMatch(r -> r.refProductId() >= setAIdBase && r.refProductId() < setAIdBase + setASize);
        boolean allFromB = !finalRows.isEmpty() && finalRows.stream()
                .allMatch(r -> r.refProductId() >= setBIdBase && r.refProductId() < setBIdBase + setBSize);

        assertThat(allFromA || allFromB)
                .as("최종 행들은 오로지 A 또는 B 한 쪽 set에서만 나와야 한다 (혼재 금지)")
                .isTrue();

        System.out.printf("writerA elapsed=%dms, writerB elapsed=%dms, finalCount=%d, winner=%s%n",
                writerAElapsed.get(), writerBElapsed.get(), finalCount, allFromA ? "A" : "B");
    }

    private List<MvProductRankRow> buildRows(int count, long refIdBase) {
        List<MvProductRankRow> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rows.add(new MvProductRankRow(
                    PERIOD_KEY,
                    i + 1,
                    refIdBase + i,
                    (double) (count - i),
                    10L, 5L, BigDecimal.valueOf(100)
            ));
        }
        return rows;
    }
}
