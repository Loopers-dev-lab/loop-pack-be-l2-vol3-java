package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetricsService;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(MySqlTestContainersConfig.class)
@DisplayName("ProductMetrics Upsert 벤치마크 테스트")
class ProductMetricsUpsertBenchmarkTest {

    private static final int SEQUENTIAL_OPS = 1000;
    private static final int CONCURRENT_THREADS = 30;
    private static final long MAX_POLL_INTERVAL_MS = 120_000L;

    @Autowired
    private ProductMetricsService productMetricsService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("순차 upsert 1000건 — p50/p99 측정 및 MAX_POLL_RECORDS 도출")
    void sequential_upsert_benchmark() {
        long[] latencies = new long[SEQUENTIAL_OPS];
        for (int i = 0; i < SEQUENTIAL_OPS; i++) {
            long start = System.nanoTime();
            productMetricsService.adjustLikeCount((long) (i % 100) + 1, 1);
            latencies[i] = System.nanoTime() - start;
        }

        long[] sorted = Arrays.copyOf(latencies, latencies.length);
        Arrays.sort(sorted);

        long p50Ms = sorted[(int) (SEQUENTIAL_OPS * 0.50)] / 1_000_000;
        long p99Ms = sorted[(int) (SEQUENTIAL_OPS * 0.99)] / 1_000_000;
        long maxPollRecords = (long) (MAX_POLL_INTERVAL_MS * 0.7 / Math.max(p99Ms, 1));

        System.out.printf("[Sequential] p50=%dms, p99=%dms, MAX_POLL_RECORDS_FORMULA=%d%n", p50Ms, p99Ms, maxPollRecords);

        assertThat(p99Ms).isLessThan(1000);
        assertThat(maxPollRecords).isGreaterThan(0);
    }

    @Test
    @DisplayName("동시 30스레드 upsert — p99 측정")
    void concurrent_upsert_benchmark() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_THREADS);
        CountDownLatch latch = new CountDownLatch(CONCURRENT_THREADS);
        List<Long> latencies = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < CONCURRENT_THREADS; i++) {
            final long productId = (long) i + 1;
            executor.submit(() -> {
                try {
                    long start = System.nanoTime();
                    productMetricsService.adjustLikeCount(productId, 1);
                    latencies.add(System.nanoTime() - start);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        long[] sorted = latencies.stream().mapToLong(Long::longValue).sorted().toArray();
        long p99Ms = sorted[(int) (sorted.length * 0.99)] / 1_000_000;

        System.out.printf("[Concurrent] threads=%d, p99=%dms%n", CONCURRENT_THREADS, p99Ms);

        assertThat(p99Ms).isLessThan(5000);
    }
}
