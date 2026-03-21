package com.loopers.application.point;

import com.loopers.domain.point.PointBalance;
import com.loopers.domain.point.PointBalanceRepository;
import com.loopers.support.error.CoreException;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ImportTestcontainers(MySqlTestContainersConfig.class)
@ActiveProfiles("test")
class PointApplicationServiceIntegrationTest {

    private static final int INITIAL_POINT_BALANCE = 1_000_000;

    @Autowired
    private PointApplicationService pointApplicationService;

    @Autowired
    private PointBalanceRepository pointBalanceRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("Point 통합: 잔액 미존재 상태에서 동시 use 요청이 와도 500 없이 정상 차감된다")
    void useConcurrentlyWhenBalanceNotInitialized() throws Exception {
        String memberId = "pointconcurrencymember";
        int threadCount = 8;
        int amountPerRequest = 1000;

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger businessFailureCount = new AtomicInteger(0);
        AtomicInteger unexpectedFailureCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executorService.execute(() -> {
                try {
                    startLatch.await();
                    pointApplicationService.use(memberId, amountPerRequest);
                    successCount.incrementAndGet();
                } catch (CoreException e) {
                    businessFailureCount.incrementAndGet();
                } catch (Exception e) {
                    unexpectedFailureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executorService.shutdown();

        PointBalance pointBalance = pointBalanceRepository.findByMemberId(memberId).orElseThrow();

        assertThat(unexpectedFailureCount.get()).isZero();
        assertThat(businessFailureCount.get()).isZero();
        assertThat(successCount.get()).isEqualTo(threadCount);
        assertThat(pointBalance.balance()).isEqualTo(INITIAL_POINT_BALANCE - (threadCount * amountPerRequest));
    }
}
