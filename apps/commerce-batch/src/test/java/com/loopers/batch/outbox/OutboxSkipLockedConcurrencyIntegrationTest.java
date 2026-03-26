package com.loopers.batch.outbox;

import com.loopers.infrastructure.outbox.OutboxEventModel;
import com.loopers.infrastructure.outbox.OutboxJpaRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.batch.job.enabled=false",
        "outbox.relay.enabled=false"
})
@Import(MySqlTestContainersConfig.class)
class OutboxSkipLockedConcurrencyIntegrationTest {

    @Autowired
    private OutboxJpaRepository outboxJpaRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("SKIP LOCKED 조회는 동시 실행 시 같은 행을 중복 획득하지 않는다.")
    void findPendingForUpdateSkipLocked_whenConcurrent_shouldNotOverlap() throws Exception {
        for (int i = 0; i < 10; i++) {
            outboxJpaRepository.save(OutboxEventModel.pending(
                    "event-" + i,
                    "product-events",
                    String.valueOf(i),
                    "TEST",
                    Instant.now(),
                    "{\"n\":" + i + "}"
            ));
        }

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<List<Long>> acquiredIds = new ArrayList<>();
        acquiredIds.add(new ArrayList<>());
        acquiredIds.add(new ArrayList<>());

        for (int t = 0; t < 2; t++) {
            int idx = t;
            pool.submit(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                transactionTemplate.executeWithoutResult(status -> {
                    outboxJpaRepository.findPendingForUpdateSkipLocked(5)
                            .forEach(e -> acquiredIds.get(idx).add(e.getId()));
                    // 락 유지 시간 확보(서로 겹치지 않는지 관찰)
                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException ignored) {
                    }
                });
                return null;
            });
        }

        ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        var set1 = new java.util.HashSet<>(acquiredIds.get(0));
        var set2 = new java.util.HashSet<>(acquiredIds.get(1));
        set1.retainAll(set2);
        assertThat(set1).isEmpty();
    }
}

